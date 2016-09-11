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
package se.llbit.chunky.ui;

import javafx.application.Platform;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.map.WorldMapLoader;
import se.llbit.chunky.renderer.OutputMode;
import se.llbit.chunky.renderer.RenderController;
import se.llbit.chunky.renderer.RenderMode;
import se.llbit.chunky.renderer.RenderStatusListener;
import se.llbit.chunky.renderer.Renderer;
import se.llbit.chunky.renderer.ResetReason;
import se.llbit.chunky.renderer.scene.RenderResetHandler;
import se.llbit.chunky.renderer.scene.Scene;
import se.llbit.chunky.renderer.StandardRenderListener;
import se.llbit.chunky.ui.render.AdvancedTab;
import se.llbit.chunky.ui.render.CameraTab;
import se.llbit.chunky.ui.render.EntitiesTab;
import se.llbit.chunky.ui.render.GeneralTab;
import se.llbit.chunky.ui.render.HelpTab;
import se.llbit.chunky.ui.render.LightingTab;
import se.llbit.chunky.ui.render.PostprocessingTab;
import se.llbit.chunky.ui.render.RenderControlTab;
import se.llbit.chunky.ui.render.SkyTab;
import se.llbit.chunky.ui.render.WaterTab;
import se.llbit.chunky.world.Icon;
import se.llbit.log.Log;
import se.llbit.util.ProgressListener;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Controller for the Render Controls dialog.
 */
public class RenderControlsFxController implements Initializable, RenderResetHandler {
  static class GUIRenderListener extends StandardRenderListener {
    private final RenderControlsFxController gui;
    private int spp;
    private int sps;

    public GUIRenderListener(RenderControlsFxController renderControls, RenderController controller,
        ProgressListener progressListener) {
      super(controller.getContext(), controller.getSceneManager(), progressListener);
      this.gui = renderControls;
    }

    @Override public void chunksLoaded() {
      Platform.runLater(() -> {
        gui.openPreview();
        gui.cameraTab.chunksLoaded();
      });
    }

    @Override public void setRenderTime(long time) {
      Platform.runLater(() -> {
        int seconds = (int) ((time / 1000) % 60);
        int minutes = (int) ((time / 60000) % 60);
        int hours = (int) (time / 3600000);
        gui.renderTimeLbl.setText(String
            .format("Render time: %d hours, %d minutes, %d seconds", hours, minutes, seconds));
      });
    }

    @Override public void setSamplesPerSecond(int sps) {
      this.sps = sps;
      updateSppStats();
    }

    @Override public void setSpp(int spp) {
      this.spp = spp;
      updateSppStats();
    }

    private void updateSppStats() {
      Platform.runLater(() -> gui.sppLbl.setText(String
          .format("%s SPP, %s SPS", gui.decimalFormat.format(spp),
              gui.decimalFormat.format(sps))));
    }

    @Override public void sceneSaved() {
    }

    @Override public void sceneLoaded() {
      Platform.runLater(() -> {
        synchronized (gui.scene) {
          gui.sceneNameField.setText(gui.scene.name());
          gui.canvas.setCanvasSize(gui.scene.width, gui.scene.height);
        }
        gui.updateTitle();
        gui.refreshSettings();
      });
    }

    @Override public void renderStateChanged(RenderMode state) {
      Platform.runLater(() -> {
        switch (state) {
          case RENDERING:
            gui.start.setSelected(true);
            break;
          case PAUSED:
            gui.pause.setSelected(true);
            break;
          case PREVIEW:
            gui.reset.setSelected(true);
            break;
        }
      });
    }

    @Override public void renderJobFinished(long time, int sps) {
      // TODO make sure this works.
      if (gui.advancedTab.shutdownAfterCompletedRender()) {
        new ShutdownAlert(null);
      }
    }
  }

  public final DecimalFormat decimalFormat = new DecimalFormat();

  /**
   * The number of milliseconds spent on rendering a scene until
   * the reset confirmation must be shown when trying to edit
   * the scene state.
   */
  private static final long SCENE_EDIT_GRACE_PERIOD = 30000;

  private Scene scene;

  private File saveFrameDirectory = new File(System.getProperty("user.dir"));

  private Stage stage;
  private RenderController renderController;
  private RenderCanvasFx canvas;
  private Renderer renderer;

  /** Used to ensure only one render reset confirm dialog is displayed at a time. */
  protected AtomicBoolean resetConfirmMutex = new AtomicBoolean(false);

  private final ProgressListener progressListener = new ProgressListener() {
    @Override public void setProgress(String task, int done, int start, int target) {
      Platform.runLater(() -> {
        progressBar.setProgress((double) done / (target - start));
        progressLbl.setText(String.format("%s: %s of %s", task, decimalFormat.format(done),
            decimalFormat.format(target)));
        etaLbl.setText("ETA: N/A");
      });
    }

    @Override public void setProgress(String task, int done, int start, int target, String eta) {
      Platform.runLater(() -> {
        progressBar.setProgress((double) done / (target - start));
        progressLbl.setText(String.format("%s: %s of %s", task, decimalFormat.format(done),
            decimalFormat.format(target)));
        etaLbl.setText("ETA: " + eta);
      });
    }
  };

  private RenderStatusListener renderTracker = RenderStatusListener.NONE;

  @FXML private TextField sceneNameField;

  @FXML private Button saveBtn;

  @FXML private ToggleButton start;
  @FXML private ToggleButton pause;
  @FXML private ToggleButton reset;

  @FXML private Button saveFrameBtn;

  @FXML private Button togglePreviewBtn;

  @FXML private ProgressBar progressBar;

  @FXML private Label progressLbl;

  @FXML private Label etaLbl;

  @FXML private Label renderTimeLbl;

  @FXML private Label sppLbl;

  @FXML private GeneralTab generalTab;

  @FXML private LightingTab lightingTab;

  @FXML private SkyTab skyTab;

  @FXML private WaterTab waterTab;

  @FXML private CameraTab cameraTab;

  @FXML private EntitiesTab entitiesTab;

  @FXML private PostprocessingTab postprocessingTab;

  @FXML private AdvancedTab advancedTab;

  @FXML private HelpTab helpTab;

  @FXML private IntegerAdjuster targetSpp;

  @FXML private Button saveDefaultSpp;

  @FXML private TabPane tabPane;
  private ChunkyFxController controller;

  public RenderControlsFxController() {
    decimalFormat.setGroupingSize(3);
    decimalFormat.setGroupingUsed(true);
  }

  @Override public void initialize(URL location, ResourceBundle resources) {
    saveBtn.setTooltip(new Tooltip("Save the current scene."));
    saveBtn.setGraphic(new ImageView(Icon.disk.fxImage()));
    saveBtn.setOnAction(e -> renderController.getSceneManager().saveScene());
    saveFrameBtn.setOnAction(this::saveCurrentFrame);
    tabPane.getSelectionModel().selectedItemProperty()
        .addListener((observable, oldValue, newValue) -> updateTab(newValue));
    tabPane.getTabs().get(0).setGraphic(new ImageView(Icon.wrench.fxImage()));
    togglePreviewBtn.setOnAction(e -> {
      if (canvas == null || !canvas.isShowing()) {
        openPreview();
      } else {
        canvas.hide();
      }
    });
    start.setGraphic(new ImageView(Icon.play.fxImage()));
    start.setTooltip(new Tooltip("Start rendering."));
    start.setOnAction(e -> scene.startRender());
    pause.setGraphic(new ImageView(Icon.pause.fxImage()));
    pause.setTooltip(new Tooltip("Pause the render."));
    pause.setOnAction(e -> scene.pauseRender());
    reset.setGraphic(new ImageView(Icon.stop.fxImage()));
    reset.setTooltip(new Tooltip("Resets the current render. Discards render progress."));
    reset.setOnAction(e -> scene.haltRender());
    sppLbl.setTooltip(new Tooltip("SPP = Samples Per Pixel, SPS = Samples Per Second"));
    targetSpp.setName("Target SPP");
    targetSpp.setTooltip("Rendering is stopped after reaching the target Samples Per Pixel (SPP).");
    targetSpp.setRange(100, 100000);
    targetSpp.makeLogarithmic();
    saveDefaultSpp.setTooltip(new Tooltip("Make the current SPP target the default."));
    saveDefaultSpp.setOnAction(e ->
        PersistentSettings.setSppTargetDefault(scene.getTargetSpp()));
  }

  private void saveCurrentFrame(Event event) {
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Save Current Frame");
    if (saveFrameDirectory != null && saveFrameDirectory.isDirectory()) {
      fileChooser.setInitialDirectory(saveFrameDirectory);
    }
    OutputMode outputMode = scene.getOutputMode();
    String extension = ".png";
    switch (outputMode) {
      case PNG:
        fileChooser.setSelectedExtensionFilter(
            new FileChooser.ExtensionFilter("PNG files", "*.png"));
        break;
      case TIFF_32:
        extension = ".tiff";
        fileChooser.setSelectedExtensionFilter(
            new FileChooser.ExtensionFilter("PNG files", "*.png"));
        break;
    }
    fileChooser.setInitialFileName(String.format("%s-%d%s",
        scene.name(), renderer.getRenderStatus().getSpp(), extension));
    File target = fileChooser.showSaveDialog(stage);
    if (target != null) {
      saveFrameDirectory = target.getParentFile();
      try {
        if (!target.getName().endsWith(extension)) {
          target = new File(target.getPath() + extension);
        }
        scene.saveFrame(target, renderTracker.taskTracker());
      } catch (IOException e1) {
        Log.error("Failed to save current frame", e1);
      }
    }
  }

  public void setStage(Stage stage) {
    this.stage = stage;
    stage.setOnHiding(e -> {
      controller.getMap().cameraPositionUpdated(); // Clear the camera view visualization.
      scene.setRenderMode(RenderMode.PAUSED);
      scene.forceReset();
      if (canvas != null && canvas.isShowing()) {
        canvas.close();
      }
    });
    stage.setOnShown(e -> {
      openPreview();
      controller.getMap().cameraPositionUpdated(); // Trigger redraw of camera view visualization.
    });
  }

  private void setRenderController(RenderController renderController) {
    this.renderTracker = new GUIRenderListener(this, renderController, progressListener);
    this.renderController = renderController;
    renderer = renderController.getRenderer();
    scene = renderController.getSceneManager().getScene();
    sceneNameField.setText(scene.name);
    sceneNameField.textProperty().addListener((observable, oldValue, newValue) -> {
      scene.setName(newValue);
      updateTitle();
    });
    generalTab.setRenderController(renderController);
    lightingTab.setRenderController(renderController);
    skyTab.setRenderController(renderController);
    waterTab.setRenderController(renderController);
    cameraTab.setRenderController(renderController);
    entitiesTab.setRenderController(renderController);
    advancedTab.setRenderController(renderController);
    postprocessingTab.setRenderController(renderController);
    targetSpp.set(scene.getTargetSpp());
    targetSpp.onValueChange(value -> scene.setTargetSpp(value));

    renderController.getSceneManager().setResetHandler(this);
    renderController.getSceneManager().setRenderStatusListener(renderTracker);
    renderer.setRenderListener(renderTracker);
  }

  private void refreshSettings() {
    targetSpp.set(scene.getTargetSpp());
    updateTab(tabPane.getSelectionModel().getSelectedItem());
  }

  private void updateTab(Tab tab) {
    RenderControlTab controlTab = (RenderControlTab) ((ScrollPane) tab.getContent()).getContent();
    controlTab.update(scene);
  }

  public void openPreview() {
    if (canvas == null) {
      canvas = new RenderCanvasFx(scene, renderer);
      EventHandler<WindowEvent> onHiding = canvas.getOnHiding();
      EventHandler<WindowEvent> onShowing = canvas.getOnShowing();
      canvas.setOnHiding(e -> {
        togglePreviewBtn.setText("Show preview window");
        onHiding.handle(e);
      });
      canvas.setOnShowing(e -> {
        togglePreviewBtn.setText("Hide preview window");
        onShowing.handle(e);
      });
      canvas.show();
      canvas.setRenderListener(renderTracker);
    } else {
      canvas.show();
      canvas.toFront();
    }
    int x = (int) (stage.getX() + stage.getWidth());
    int y = (int) stage.getY();
    canvas.setX(x);
    canvas.setY(y);
    canvas.repaint();
  }

  public void setMapLoader(WorldMapLoader mapLoader) {
    generalTab.setFxController(this);
    generalTab.setMapLoader(mapLoader);
    cameraTab.setMapLoader(mapLoader);
    updateTab(tabPane.getSelectionModel().getSelectedItem());
  }

  public RenderCanvasFx getCanvas() {
    return canvas;
  }

  public void setController(ChunkyFxController controller) {
    this.controller = controller;
    generalTab.setChunkyFxController(controller);
    setRenderController(controller.getChunky().getRenderController());
    setMapLoader(controller.getMapLoader());
  }

  private void updateTitle() {
    stage.setTitle("Render Controls - " + scene.name());
  }

  @Override  public boolean allowSceneRefresh() {
    if (scene.getResetReason() == ResetReason.SCENE_LOADED
        || renderer.getRenderStatus().getRenderTime() < SCENE_EDIT_GRACE_PERIOD) {
      return true;
    } else {
      requestRenderReset();
    }
    return false;
  }

  private void requestRenderReset() {
    if (resetConfirmMutex.compareAndSet(false, true)) {
      Platform.runLater(() -> {
        try {
          ConfirmResetPopup popup = new ConfirmResetPopup(
              () -> {
                // On accept.
                renderController.getSceneManager().applySceneChanges();
                resetConfirmMutex.set(false);
              },
              () -> {
                // On reject.
                renderController.getSceneManager().discardSceneChanges();
                refreshSettings();
                resetConfirmMutex.set(false);
              });
          popup.show(stage);
        } catch (IOException e) {
          Log.warn("Could not open reset confirmation dialog.", e);
        }
      });
    }
  }

}
