/* Copyright (c) 2016 Jesper Öqvist <jesper@llbit.se>
 *
 * This file is part of Chunky.
 *
 * Chunky is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Chunky is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with Chunky.  If not, see <http://www.gnu.org/licenses/>.
 */
package se.llbit.chunky.renderer.scene;

import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.RenderMode;
import se.llbit.chunky.renderer.RenderStatus;
import se.llbit.chunky.renderer.RenderStatusListener;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.SceneProvider;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.log.Log;
import se.llbit.util.TaskTracker;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * A synchronous scene manager runs its operations on the calling thread.
 *
 * <p>The scene manager stores the current scene state and pending
 * scene state changes. The scene manager is responsible for protecting
 * parts of the scene data from concurrent writes & reads by
 * the user (through the UI) and renderer.
 */
public class SynchronousSceneManager implements SceneProvider, SceneManager {
  /**
   * This stores all pending scene state changes. When the scene edit
   * grace period has expired any changes to this scene state are not
   * copied directly to the stored scene state.
   *
   * Multiple threads can try to read/write the mutable scene concurrently,
   * so multiple accesses are serialized by the intrinsic lock of the Scene
   * class.
   *
   * NB: lock ordering for scene and storedScene is always scene->storedScene!
   */
  private final Scene scene;

  /**
   * Stores the current scene configuration. When the scene edit grace period has
   * expired a reset confirm dialog will be shown before applying any further
   * non-transitory changes to the stored scene state.
   */
  private final Scene storedScene;

  private final RenderContext context;

  private final Renderer renderer;

  private RenderStatusListener renderStatusListener = RenderStatusListener.NONE;

  private RenderResetHandler resetHandler = () -> true;

  public SynchronousSceneManager(RenderContext context, Renderer renderer) {
    this.context = context;
    this.renderer = renderer;

    scene = SceneFactory.instance.newScene();

    // The stored scene is a copy of the mutable scene. They even share
    // some data structures that are only used by the renderer.
    storedScene = SceneFactory.instance.copyScene(scene);
  }

  public void setRenderStatusListener(RenderStatusListener renderStatusListener) {
    this.renderStatusListener = renderStatusListener;
  }

  public void setResetHandler(RenderResetHandler resetHandler) {
    this.resetHandler = resetHandler;
  }

  /**
   * This should only be used by the render controls dialog controller.
   * Modifications to the scene must always be protected by the intrinsic
   * lock of the scene object.
   */
  public Scene getScene() {
    return scene;
  }

  /**
   * Save the current scene
   *
   * @throws InterruptedException
   */
  @Override public void saveScene() throws InterruptedException {
    try {
      synchronized (storedScene) {
        String sceneName = storedScene.name();
        Log.info("Saving scene " + sceneName);

        // Create backup of scene description and current render dump.
        storedScene.backupFile(context, context.getSceneDescriptionFile(sceneName));
        storedScene.backupFile(context, sceneName + ".dump");

        // Copy render status over from the renderer.
        RenderStatus status = renderer.getRenderStatus();
        storedScene.renderTime = status.getRenderTime();
        storedScene.spp = status.getSpp();
        storedScene.saveScene(context, renderStatusListener);
        Log.info("Scene saved");
      }
      renderStatusListener.sceneSaved();
    } catch (IOException e) {
      Log.warn("Failed to save scene. Reason: " + e.getMessage(), e);
    }
  }

  /**
   * Load a saved scene.
   */
  @Override public void loadScene(String sceneName)
      throws IOException, SceneLoadingError, InterruptedException {

    // Do not change lock ordering here.
    // Lock order: scene -> storedScene.
    synchronized (scene) {
      try (TaskTracker.Task ignored = renderStatusListener.taskTracker().task("Loading scene", 1)) {
        scene.loadScene(context, renderStatusListener, sceneName);
      }

      // Update progress bar.
      renderStatusListener.renderTask().update("Rendering", scene.getTargetSpp(), scene.spp);

      scene.setResetReason(ResetReason.SCENE_LOADED);

      // Wake up waiting threads in awaitSceneStateChange().
      scene.notifyAll();

      renderStatusListener.sceneLoaded();
      renderStatusListener.renderStateChanged(scene.getMode());
    }
  }

  /**
   * Load chunks and reset camera and scene.
   * The scene name should be set before the call to loadFreshChunks().
   */
  protected void loadFreshChunks(World world, Collection<ChunkPosition> chunksToLoad) {
    synchronized (scene) {
      scene.clear();
      scene.loadChunks(renderStatusListener.taskTracker(), world, chunksToLoad);
      scene.moveCameraToCenter();
      scene.refresh();
      scene.setResetReason(ResetReason.SCENE_LOADED);
      scene.setRenderMode(RenderMode.PREVIEW);
    }
    renderStatusListener.sceneLoaded();
  }

  /**
   * Load chunks without resetting the current scene.
   * This preserves camera position, etc.
   */
  protected void loadChunks(World world, Collection<ChunkPosition> chunksToLoad) {
    synchronized (scene) {
      scene.loadChunks(renderStatusListener.taskTracker(), world, chunksToLoad);
      scene.refresh();
      scene.setResetReason(ResetReason.SCENE_LOADED);
      scene.setRenderMode(RenderMode.PREVIEW);
    }
    renderStatusListener.chunksLoaded();
  }

  /**
   * Attempt to reload all loaded chunks.
   */
  protected void reloadChunks() {
    synchronized (scene) {
      scene.reloadChunks(renderStatusListener.taskTracker());
      scene.refresh();
      scene.setResetReason(ResetReason.SCENE_LOADED);
      scene.setRenderMode(RenderMode.PREVIEW);
    }
    renderStatusListener.chunksLoaded();
  }

  @Override public ResetReason awaitSceneStateChange() throws InterruptedException {
    synchronized (scene) {
      while (true) {
        if (scene.shouldRefresh() && (scene.getForceReset() || resetHandler.allowSceneRefresh())) {
          synchronized (storedScene) {
            storedScene.copyState(scene);
            storedScene.mode = scene.mode;
          }
          ResetReason reason = scene.getResetReason();
          scene.clearResetFlags();
          return reason;
        } else if (scene.getMode() != storedScene.getMode()) {
          // Make sure the renderer sees the updated render mode.
          // TODO: handle buffer finalization updates as state change.
          synchronized (storedScene) {
            storedScene.mode = scene.mode;
          }
          return ResetReason.MODE_CHANGE;
        }
        scene.wait();
      }
    }
  }

  @Override public boolean pollSceneStateChange() {
    if (scene.shouldRefresh() && (scene.getForceReset() || resetHandler.allowSceneRefresh())) {
      return true;
    } else if (scene.getMode() != storedScene.getMode()) {
      return true;
    }
    return false;
  }

  @Override public void withSceneProtected(Consumer<Scene> fun) {
    // Lock order: scene -> storedScene;
    synchronized (scene) {
      synchronized (storedScene) {
        storedScene.copyTransients(scene);
        fun.accept(storedScene);
      }
    }
  }

  @Override public void withEditSceneProtected(Consumer<Scene> fun) {
    synchronized (scene) {
      fun.accept(scene);
    }
  }
  /**
   * Merge a render dump into the current render.
   *
   * @param dumpFile the file to be merged.
   */
  protected void mergeDump(File dumpFile) {
    synchronized (scene) {
      renderer.withSampleBufferProtected((samples, width, height) ->{
        if (width != scene.width || height != scene.height) {
          throw new Error("Failed to merge render dump - wrong canvas size.");
        }
        scene.mergeDump(dumpFile, renderStatusListener);
      });
      scene.setResetReason(ResetReason.SCENE_LOADED);
    }
  }

  /**
   * Discard pending scene changes.
   */
  public void applySceneChanges() {
    // Lock order: scene -> storedScene.
    synchronized (scene) {
      synchronized (storedScene) {
        // Setting SCENE_LOADED will force the reset.
        scene.setResetReason(ResetReason.SCENE_LOADED);

        // Wake up the threads waiting in awaitSceneStateChange().
        scene.notifyAll();
      }
    }
  }

  /**
   * Apply pending scene changes.
   */
  public void discardSceneChanges() {
    // Lock order: scene -> storedScene.
    synchronized (scene) {
      synchronized (storedScene) {
        scene.copyState(storedScene);
        scene.clearResetFlags();
      }
    }
  }

}
