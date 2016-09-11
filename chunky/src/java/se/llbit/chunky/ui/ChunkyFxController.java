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

import com.sun.deploy.uitoolkit.impl.fx.HostServicesFactory;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.converter.NumberStringConverter;
import se.llbit.chunky.PersistentSettings;
import se.llbit.chunky.launcher.LauncherSettings;
import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.main.ChunkyOptions;
import se.llbit.chunky.map.WorldMapLoader;
import se.llbit.chunky.renderer.ChunkViewListener;
import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.scene.AsynchronousSceneManager;
import se.llbit.chunky.renderer.scene.Camera;
import se.llbit.chunky.renderer.scene.SceneDescription;
import se.llbit.chunky.resources.MinecraftFinder;
import se.llbit.chunky.resources.TexturePackLoader;
import se.llbit.chunky.ui.render.RenderControlsFx;
import se.llbit.chunky.world.Block;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.ChunkSelectionListener;
import se.llbit.chunky.world.ChunkView;
import se.llbit.chunky.world.Icon;
import se.llbit.chunky.world.World;
import se.llbit.chunky.world.listeners.ChunkUpdateListener;
import se.llbit.fxutil.GroupedChangeListener;
import se.llbit.log.Level;
import se.llbit.log.Log;
import se.llbit.math.Vector3;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Collection;
import java.util.ResourceBundle;

/**
 * Controller for the main Chunky window.
 */
public class ChunkyFxController
    implements Initializable, ChunkViewListener, ChunkSelectionListener, ChunkUpdateListener {

  private Chunky chunky;
  private WorldMapLoader mapLoader;
  private ChunkMap map;
  private Minimap minimap;

  @FXML private Canvas mapCanvas;
  @FXML private Canvas mapOverlay;

  @FXML private Canvas minimapCanvas;

  @FXML private MenuItem menuExit;

  @FXML private BorderPane borderPane;

  @FXML private Button clearSelectionBtn;

  @FXML private Button changeWorldBtn;

  @FXML private Button reloadWorldBtn;

  @FXML private ToggleButton overworldBtn;

  @FXML private ToggleButton netherBtn;

  @FXML private ToggleButton endBtn;

  @FXML private ChoiceBox<MapViewMode> mapViewCb;

  @FXML private TextField scaleField;

  @FXML private TextField layerField;

  @FXML private Slider scaleSlider;

  @FXML private Slider layerSlider;

  @FXML private ToggleButton trackPlayerBtn;

  @FXML private ToggleButton trackCameraBtn;

  @FXML private Tab mapViewTab;

  @FXML private Tab chunksTab;

  @FXML private Tab optionsTab;

  @FXML private Tab renderTab;

  @FXML private Tab aboutTab;

  @FXML private CheckBox highlightBtn;

  @FXML private ChoiceBox<Block> highlightCb;

  @FXML private SimpleColorPicker highlightColor;

  @FXML private Button loadResourcePackBtn;

  @FXML private Button loadDefaultTexturesBtn;

  @FXML private CheckBox singleColorBtn;

  @FXML private CheckBox showLauncherBtn;

  @FXML private Button clearSelectionBtn2;

  @FXML private Button newSceneBtn;

  @FXML private Button loadSceneBtn;

  @FXML private Button openSceneDirBtn;

  @FXML private Button changeSceneDirBtn;

  @FXML private Hyperlink documentationLink;

  @FXML private Hyperlink gitHubLink;

  @FXML private Hyperlink issueTrackerLink;

  @FXML private Hyperlink forumLink;

  @FXML private Button creditsBtn;

  @FXML private TextField xPosition;

  @FXML private TextField zPosition;
  @FXML private Button deleteChunks;
  @FXML private Button exportZip;
  @FXML private Button renderPng;
  @FXML private StackPane mapPane;
  @FXML private StackPane minimapPane;

  private RenderControlsFx controls = null;
  private Stage stage;

  public ChunkyFxController() {
    mapLoader = new WorldMapLoader(this);
    chunky = new Chunky(ChunkyOptions.getDefaults());
    map = new ChunkMap(mapLoader, this);
    minimap = new Minimap(mapLoader, this);
    mapLoader.addViewListener(this);
    mapLoader.addViewListener(map);
    mapLoader.addViewListener(minimap);
    mapLoader.getChunkSelection().addSelectionListener(this);
    mapLoader.getChunkSelection().addChunkUpdateListener(map);
    mapLoader.getChunkSelection().addChunkUpdateListener(minimap);
    mapLoader.addWorldLoadListener(() -> {
      map.redrawMap();
      minimap.redrawMap();
    });
  }

  @Override public void initialize(URL fxmlUrl, ResourceBundle resources) {
    Log.setReceiver(new UILogReceiver(), Level.ERROR, Level.WARNING);
    map.setCanvas(mapCanvas);
    minimap.setCanvas(minimapCanvas);

    mapPane.widthProperty().addListener((observable, oldValue, newValue) -> {
      mapCanvas.setWidth(newValue.doubleValue());
      mapOverlay.setWidth(newValue.doubleValue());
      mapLoader.setMapSize((int) mapCanvas.getWidth(), (int) mapCanvas.getHeight());
    });
    mapPane.heightProperty().addListener((observable, oldValue, newValue) -> {
      mapCanvas.setHeight(newValue.doubleValue());
      mapOverlay.setHeight(newValue.doubleValue());
      mapLoader.setMapSize((int) mapCanvas.getWidth(), (int) mapCanvas.getHeight());
    });

    minimapPane.widthProperty().addListener((observable, oldValue, newValue) -> {
      minimapCanvas.setWidth(newValue.doubleValue());
      mapLoader.setMinimapSize((int) minimapCanvas.getWidth(), (int) minimapCanvas.getHeight());
    });
    minimapPane.heightProperty().addListener((observable, oldValue, newValue) -> {
      minimapCanvas.setHeight(newValue.doubleValue());
      mapLoader.setMinimapSize((int) minimapCanvas.getWidth(), (int) minimapCanvas.getHeight());
    });
    mapOverlay.setOnMouseExited(e -> map.tooltip.hide());

    // Set up property bindings for the map view.
    ChunkView mapView = map.getView();  // Initial map view - only used to initialize controls.

    // A scale factor of 16 is used to convert map positions between block/chunk coordinates.
    DoubleProperty xProperty = new SimpleDoubleProperty(mapView.x);
    DoubleProperty zProperty = new SimpleDoubleProperty(mapView.z);
    IntegerProperty scaleProperty = new SimpleIntegerProperty(mapView.scale);
    IntegerProperty layerProperty = new SimpleIntegerProperty(mapView.layer);

    // Bind controls with properties.
    xPosition.textProperty().bindBidirectional(xProperty, new NumberStringConverter());
    zPosition.textProperty().bindBidirectional(zProperty, new NumberStringConverter());
    scaleField.textProperty().bindBidirectional(scaleProperty, new NumberStringConverter());
    scaleSlider.valueProperty().bindBidirectional(scaleProperty);
    layerField.textProperty().bindBidirectional(layerProperty, new NumberStringConverter());
    layerSlider.valueProperty().bindBidirectional(layerProperty);

    // Add listeners to the properties to control the map view.
    GroupedChangeListener<Object> group = new GroupedChangeListener<>(null);
    xProperty.addListener(new GroupedChangeListener<>(group, (observable, oldValue, newValue) -> {
      ChunkView view = mapLoader.getMapView();
      mapLoader.panTo(newValue.doubleValue() / 16, view.z);
    }));
    zProperty.addListener(new GroupedChangeListener<>(group, (observable, oldValue, newValue) -> {
      ChunkView view = mapLoader.getMapView();
      mapLoader.panTo(view.x, newValue.doubleValue() / 16);
    }));
    scaleProperty.addListener(new GroupedChangeListener<>(group,
        (observable, oldValue, newValue) -> mapLoader.setScale(newValue.intValue())));
    layerProperty.addListener(new GroupedChangeListener<>(group,
        (observable, oldValue, newValue) -> mapLoader.setLayer(newValue.intValue())));

    // Add map view listener to control the individual value properties.
    mapLoader.getMapViewProperty().addListener(new GroupedChangeListener<>(group,
        (observable, oldValue, newValue) -> {
          xProperty.set(newValue.x * 16);
          zProperty.set(newValue.z * 16);
          scaleProperty.set(newValue.scale);
          layerProperty.set(newValue.layer);
        }));

    clearSelectionBtn2.setOnAction(e -> mapLoader.clearChunkSelection());

    deleteChunks.setTooltip(new Tooltip("Delete selected chunks."));
    deleteChunks.setOnAction(e -> {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("Delete Selected Chunks");
      alert.setContentText(
          "Do you really want to delete the selected chunks? This can not be undone.");
      if (alert.showAndWait().get() == ButtonType.OK) {
        mapLoader.deleteSelectedChunks(ProgressTracker.NONE);
      }
    });

    exportZip.setTooltip(new Tooltip("Export selected chunks to Zip archive."));
    exportZip.setOnAction(e -> {
      FileChooser fileChooser = new FileChooser();
      fileChooser.setTitle("Export Chunks to Zip");
      fileChooser.setSelectedExtensionFilter(new FileChooser.ExtensionFilter("Zip files", "*.zip"));
      fileChooser.setInitialFileName(String.format("%s.zip", mapLoader.getWorldName()));
      File target = fileChooser.showSaveDialog(stage);
      if (target != null) {
        mapLoader.exportZip(target, ProgressTracker.NONE);
      }
    });

    renderPng.setOnAction(e -> {
      FileChooser fileChooser = new FileChooser();
      fileChooser.setTitle("Export PNG");
      fileChooser
          .setSelectedExtensionFilter(new FileChooser.ExtensionFilter("PNG images", "*.png"));
      fileChooser.setInitialFileName(String.format("%s.png", mapLoader.getWorldName()));
      File target = fileChooser.showSaveDialog(stage);
      if (target != null) {
        map.renderView(target, ProgressTracker.NONE);
      }
    });

    newSceneBtn.setOnAction(e -> createNew3DScene());
    loadSceneBtn.setGraphic(new ImageView(Icon.load.fxImage()));
    loadSceneBtn.setOnAction(e -> loadScene());

    openSceneDirBtn.setOnAction(e -> {
      try {
        if (Desktop.isDesktopSupported()) {
          File sceneDir = SceneDirectoryPicker.getCurrentSceneDirectory();
          if (sceneDir != null) {
            Desktop.getDesktop().open(sceneDir);
          }
        } else {
          Log.warn("Can not open system file browser.");
        }
      } catch (IOException e1) {
        Log.warn("Failed to open scene directory.", e1);
      }
    });

    changeSceneDirBtn.setOnAction(e -> SceneDirectoryPicker.changeSceneDirectory(chunky.options));

    creditsBtn.setOnAction(e -> {
      try {
        Credits credits = new Credits();
        credits.show();
      } catch (IOException e1) {
        Log.warn("Failed to create credits window.", e1);
      }
    });

    mapViewTab.setGraphic(new ImageView(Icon.map.fxImage()));
    chunksTab.setGraphic(new ImageView(Icon.mapSelected.fxImage()));
    optionsTab.setGraphic(new ImageView(Icon.wrench.fxImage()));
    renderTab.setGraphic(new ImageView(Icon.sky.fxImage()));
    aboutTab.setGraphic(new ImageView(Icon.question.fxImage()));

    loadResourcePackBtn
        .setTooltip(new Tooltip("Select which resource pack Chunky uses to load block textures."));
    loadResourcePackBtn.setGraphic(new ImageView(Icon.load.fxImage()));

    loadDefaultTexturesBtn
        .setTooltip(new Tooltip("Load default Minecraft textures from Minecraft installation."));
    loadDefaultTexturesBtn.setOnAction(e -> {
      try {
        TexturePackLoader.loadTexturePack(MinecraftFinder.getMinecraftJarNonNull(), true);
        mapLoader.reloadWorld();
      } catch (FileNotFoundException e1) {
        Log.warn("Minecraft Jar not found! Using placeholder textures.");
      } catch (TexturePackLoader.TextureLoadingError e1) {
        Log.warn("Failed to load default texture pack! Using placeholder textures.");
      }
    });

    LauncherSettings settings = new LauncherSettings();
    settings.load();
    showLauncherBtn
        .setTooltip(new Tooltip("Opens the Chunky launcher when starting Chunky next time."));
    showLauncherBtn.setSelected(settings.showLauncher);
    showLauncherBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
      LauncherSettings launcherSettings = new LauncherSettings();
      launcherSettings.load();
      launcherSettings.showLauncher = newValue;
      launcherSettings.save();
    });

    singleColorBtn.setSelected(PersistentSettings.getSingleColorTextures());
    singleColorBtn.selectedProperty().addListener((observable, oldValue, newValue) -> {
      PersistentSettings.setSingleColorTextures(newValue);
    });

    highlightBtn.setTooltip(new Tooltip("Highlight the selected block type in the current layer."));
    highlightBtn.selectedProperty().bindBidirectional(mapLoader.highlightEnabledProperty());

    highlightCb.getItems()
        .addAll(Block.DIRT, Block.GRASS, Block.STONE, Block.COBBLESTONE, Block.MOSSSTONE,
            Block.IRONORE, Block.COALORE, Block.REDSTONEORE, Block.DIAMONDORE, Block.GOLDORE,
            Block.MONSTERSPAWNER, Block.BRICKS, Block.CLAY, Block.LAPISLAZULIORE, Block.EMERALDORE,
            Block.NETHERQUARTZORE);
    highlightCb.getSelectionModel().select(Block.DIAMONDORE);
    highlightCb.getSelectionModel().selectedItemProperty().addListener((item, prev, next) -> {
      mapLoader.highlightEnabledProperty().set(true);
      mapLoader.highlightBlock(next);
    });

    highlightColor.setColor(mapLoader.highlightColor());
    highlightColor.setTooltip(new Tooltip("Choose highlight color"));
    highlightColor.colorProperty().addListener(
        (observable, oldValue, newValue) -> mapLoader.highlightColor(newValue));

    trackPlayerBtn.selectedProperty().bindBidirectional(mapLoader.trackPlayerProperty());
    trackCameraBtn.selectedProperty().bindBidirectional(mapLoader.trackCameraProperty());

    overworldBtn.setSelected(mapLoader.getDimension() == World.OVERWORLD_DIMENSION);
    overworldBtn.setTooltip(new Tooltip("Full of grass and Creepers!"));

    netherBtn.setSelected(mapLoader.getDimension() == World.NETHER_DIMENSION);
    netherBtn.setTooltip(new Tooltip("The land of Zombie Pigmen."));

    endBtn.setSelected(mapLoader.getDimension() == World.END_DIMENSION);
    endBtn.setTooltip(new Tooltip("Watch out for the dragon."));

    changeWorldBtn.setOnAction(e -> {
      try {
        WorldChooser worldChooser = new WorldChooser(mapLoader);
        worldChooser.show();
      } catch (IOException e1) {
        Log.error("Failed to create world chooser window.", e1);
      }
    });

    reloadWorldBtn.setGraphic(new ImageView(Icon.reload.fxImage()));
    reloadWorldBtn.setOnAction(e -> mapLoader.reloadWorld());

    overworldBtn.setGraphic(new ImageView(Icon.grass.fxImage()));
    overworldBtn.setOnAction(e -> mapLoader.setDimension(World.OVERWORLD_DIMENSION));

    netherBtn.setGraphic(new ImageView(Icon.netherrack.fxImage()));
    netherBtn.setOnAction(e -> mapLoader.setDimension(World.NETHER_DIMENSION));

    endBtn.setGraphic(new ImageView(Icon.endStone.fxImage()));
    endBtn.setOnAction(e -> mapLoader.setDimension(World.END_DIMENSION));

    mapViewCb.getItems().addAll(MapViewMode.values());
    mapViewCb.getSelectionModel().select(MapViewMode.AUTO);
    mapViewCb.getSelectionModel().selectedItemProperty()
        .addListener((item, prev, next) -> mapLoader.setRenderer(next.getRenderer()));

    menuExit.setAccelerator(new KeyCodeCombination(KeyCode.Q, KeyCombination.CONTROL_DOWN));
    clearSelectionBtn.setOnAction(event -> mapLoader.clearChunkSelection());

    mapLoader.setMapSize((int) mapCanvas.getWidth(), (int) mapCanvas.getHeight());
    mapLoader.setMinimapSize((int) minimapCanvas.getWidth(), (int) minimapCanvas.getHeight());
    mapOverlay.setOnScroll(map::onScroll);
    mapOverlay.setOnMousePressed(map::onMousePressed);
    mapOverlay.setOnMouseReleased(map::onMouseReleased);
    mapOverlay.setOnMouseMoved(map::onMouseMoved);
    mapOverlay.setOnMouseDragged(map::onMouseDragged);
    mapOverlay.addEventFilter(MouseEvent.ANY, event -> mapOverlay.requestFocus());
    mapOverlay.setOnKeyPressed(map::onKeyPressed);
    mapOverlay.setOnKeyReleased(map::onKeyReleased);
    minimapCanvas.setOnMousePressed(minimap::onMousePressed);

    mapLoader.loadWorld(PersistentSettings.getLastWorld());
    Platform.runLater(() -> {
      try {
        TexturePackLoader.loadTexturePack(new File(chunky.options.texturePack), false);
      } catch (TexturePackLoader.TextureLoadingError e) {
        Log.error("Failed to load texture pack.", e);
      }
    });
  }

  public void setStageAndScene(ChunkyFx app, Stage stage, Scene scene) {
    this.stage = stage;
    documentationLink.setOnAction(
        e -> HostServicesFactory.getInstance(app).showDocument("http://chunky.llbit.se"));

    issueTrackerLink.setOnAction(e -> HostServicesFactory.getInstance(app)
        .showDocument("https://github.com/llbit/chunky/issues"));

    gitHubLink.setOnAction(
        e -> HostServicesFactory.getInstance(app).showDocument("https://github.com/llbit/chunky"));

    forumLink.setOnAction(
        e -> HostServicesFactory.getInstance(app).showDocument("https://www.reddit.com/r/chunky"));

    stage.setOnCloseRequest(event -> {
      Platform.exit();
      System.exit(0);
    });
    menuExit.setOnAction(event -> {
      Platform.exit();
      System.exit(0);
    });

    loadResourcePackBtn.setOnAction(e -> {
      FileChooser fileChooser = new FileChooser();
      fileChooser.setTitle("Choose Resource Pack");
      fileChooser
          .setSelectedExtensionFilter(new FileChooser.ExtensionFilter("Resource Packs", "*.zip"));
      File resourcePack = fileChooser.showOpenDialog(stage);
      if (resourcePack != null) {
        try {
          TexturePackLoader.loadTexturePack(resourcePack, true);
          mapLoader.reloadWorld();
        } catch (TexturePackLoader.TextureLoadingError e1) {
          Log.warn("Failed to load textures from selected resource pack.");
        }
      }
    });
    borderPane.prefHeightProperty().bind(scene.heightProperty());
    borderPane.prefWidthProperty().bind(scene.widthProperty());
  }

  /**
   * Open the 3D chunk view.
   */
  private synchronized void open3DView() {
    try {
      if (controls == null) {
        controls = new RenderControlsFx(this);
        controls.show();
      } else {
        controls.show();
        controls.toFront();
      }
    } catch (IOException e) {
      Log.error("Failed to create render controls window.", e);
    }
  }

  public void createNew3DScene() {
    if (hasActiveRenderControls()) {
      Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
      alert.setTitle("Create New Scene");
      alert.setHeaderText("Overwrite existing scene?");
      alert.setContentText(
          "It seems like a scene already exists. Do you wish to overwrite it?");
      if (alert.showAndWait().get() != ButtonType.OK) {
        return;
      }
    }

    // Choose a default scene name.
    World world = mapLoader.getWorld();
    RenderContext context = chunky.getRenderContext();
    String preferredName =
        AsynchronousSceneManager.preferredSceneName(context, world.levelName());
    if (!AsynchronousSceneManager.sceneNameIsValid(preferredName)
        || !AsynchronousSceneManager.sceneNameIsAvailable(context, preferredName)) {
      preferredName = "Untitled Scene";
    }

    // Reset the scene state to the default scene state.
    chunky.getRenderController().getSceneManager().getScene().initializeNewScene(preferredName);

    // Show the render controls etc.
    open3DView();

    // Load selected chunks.
    Collection<ChunkPosition> selection = mapLoader.getChunkSelection().getSelection();
    if (!selection.isEmpty()) {
      chunky.getSceneManager().loadFreshChunks(mapLoader.getWorld(), selection);
    }
  }

  public void loadScene(SceneDescription scene) {
    open3DView();
    chunky.getSceneManager().loadScene(scene.name);
  }

  /**
   * Show the scene selector dialog.
   */
  public void loadScene() {
    try {
      SceneChooser chooser = new SceneChooser(this, chunky.getSceneManager());
      chooser.show();
    } catch (IOException e) {
      Log.error("Failed to create scene chooser window.", e);
    }
  }

  public void panToCamera() {
    chunky.getRenderController().getSceneProvider()
        .withSceneProtected(scene -> mapLoader.panTo(scene.camera().getPosition()));
  }

  public void moveCameraTo(double x, double z) {
    chunky.getRenderController().getSceneProvider().withEditSceneProtected(scene -> {
      Camera camera = scene.camera();
      Vector3 pos = new Vector3(x, camera.getPosition().y, z);
      camera.setPosition(pos);
    });
  }

  public boolean hasActiveRenderControls() {
    return controls != null && controls.isShowing();
  }

  public ChunkMap getMap() {
    return map;
  }

  @Override public void viewUpdated() {
  }

  @Override public void layerChanged(int layer) {
  }

  @Override public void viewMoved() {
    mapLoader.trackPlayerProperty().set(false);
    mapLoader.trackCameraProperty().set(false);
  }

  @Override public void cameraPositionUpdated() {
  }

  @Override public void chunkSelectionChanged() {
  }

  public Canvas getMinimapCanvas() {
    return minimapCanvas;
  }

  @Override public void regionUpdated(ChunkPosition region) {
  }

  @Override public void chunkUpdated(ChunkPosition region) {
  }

  public Minimap getMinimap() {
    return minimap;
  }

  public Chunky getChunky() {
    return chunky;
  }

  public WorldMapLoader getMapLoader() {
    return mapLoader;
  }

  public Canvas getMapOverlay() {
    return mapOverlay;
  }
}
