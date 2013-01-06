/* Copyright (c) 2012 Jesper Öqvist <jesper@llbit.se>
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
package se.llbit.chunky.renderer.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Collection;

import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.GroupLayout.Alignment;
import javax.swing.LayoutStyle.ComponentPlacement;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import org.apache.log4j.Logger;

import se.llbit.chunky.main.Chunky;
import se.llbit.chunky.renderer.Camera;
import se.llbit.chunky.renderer.RenderContext;
import se.llbit.chunky.renderer.RenderManager;
import se.llbit.chunky.renderer.RenderStatusListener;
import se.llbit.chunky.renderer.Scene;
import se.llbit.chunky.renderer.SceneManager;
import se.llbit.chunky.renderer.Sun;
import se.llbit.chunky.ui.CenteredFileDialog;
import se.llbit.chunky.world.ChunkPosition;
import se.llbit.chunky.world.World;
import se.llbit.util.ProgramProperties;

/**
 * Render Controls dialog.
 * @author Jesper Öqvist <jesper@llbit.se>
 */
@SuppressWarnings("serial")
public class RenderControls extends JDialog implements ViewListener,
	RenderStatusListener {
	
	private static final Logger logger =
			Logger.getLogger(RenderControls.class);
	
	private static final int[] dumpFrequencies = { 50, 100, 500, 1000 };
	
	private final RenderManager renderManager;
	private final SceneManager sceneManager;
	private final Chunk3DView view;
	private final Chunky chunky;
	
	/**
	 * Number format for current locale.
	 */
	private NumberFormat numberFormat =
			NumberFormat.getInstance();
	
	private JSlider sunYawSlider;
	private JSlider skyRotation;
	private JSlider sunPitchSlider;
	private JSlider focalOffsetSlider;
	private JSlider fovSlider;
	private JButton loadSkymapBtn;
	private JTextField widthField;
	private JTextField heightField;
	private JSlider dofSlider;
	private JTextField fovField;
	private JTextField dofField;
	private JTextField focalOffsetField;
	private JButton startRenderBtn = new JButton();
	private JCheckBox enableEmitters;
	private JCheckBox directLight;
	private JButton saveSceneBtn;
	private JButton loadSceneBtn;
	private JButton saveFrameBtn;
	private JCheckBox stillWaterCB;
	private JTextField sceneNameField;
	private JTextField rayDepthField;
	private JSlider rayDepthSlider;
	private JLabel sceneNameLbl;
	private JCheckBox biomeColorsCB;
	private JButton stopRenderBtn = new JButton();
	private JCheckBox clearWaterCB;
	private JSlider sunIntensitySlider;
	private JSlider emitterIntensitySlider;
	private JCheckBox atmosphereEnabled;
	private JCheckBox volumetricFogEnabled;
	private JSlider gammaSlider;
	private JTextField gammaField;
	private RenderContext context;
	private JButton showPreviewBtn;
	private JLabel renderTimeLbl;
	private JLabel samplesPerSecondLbl;
	private JLabel sppLbl;
	private JProgressBar progressBar;
	private JLabel progressLbl;

	private JLabel etaLbl;

	private JCheckBox waterWorldCB;

	private JTextField waterHeightField;
	
	private final DecimalFormat decimalFormat;

	private JCheckBox saveDumpsCB;

	private JComboBox dumpFrequency;

	private JTextField sppTargetField;

	/**
	 * Create a new Render Controls dialog.
	 * @param chunkyInstance 
	 * @param renderContext 
	 */
	public RenderControls(Chunky chunkyInstance, RenderContext renderContext) {
		
		super(chunkyInstance.getFrame());
		
		decimalFormat = new DecimalFormat();
		decimalFormat.setGroupingSize(3);
		decimalFormat.setGroupingUsed(true);
		
		context = renderContext;
		chunky = chunkyInstance;
		
		view = new Chunk3DView(this, chunkyInstance.getFrame());
		
		renderManager = new RenderManager(
				view.getCanvas(), renderContext, this);
		renderManager.start();
		
		view.setRenderer(renderManager);
		
		sceneManager = new SceneManager(renderManager);
		sceneManager.start();
		
		setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		setModalityType(ModalityType.MODELESS);
		
		addWindowListener(new WindowListener() {
			@Override
			public void windowOpened(WindowEvent e) {
			}
			@Override
			public void windowIconified(WindowEvent e) {
			}
			@Override
			public void windowDeiconified(WindowEvent e) {
			}
			@Override
			public void windowDeactivated(WindowEvent e) {
			}
			@Override
			public void windowClosing(WindowEvent e) {
				sceneManager.interrupt();
				RenderControls.this.dispose();
			}
			@Override
			public void windowClosed(WindowEvent e) {
				// halt rendering
				renderManager.interrupt();
				
				// dispose of the 3D view
				view.setVisible(false);
				view.dispose();
			}
			@Override
			public void windowActivated(WindowEvent e) {
			}
		});
		
		updateTitle();
		
		JTabbedPane tabbedPane = new JTabbedPane();
		
		tabbedPane.addTab("General", buildGeneralPane());
		tabbedPane.addTab("Lighting", buildLightingPane());
		tabbedPane.addTab("Sky", buildSkyPane());
		tabbedPane.addTab("Camera", buildCameraPane());
		tabbedPane.addTab("Advanced", buildAdvancedPane());
		
		JLabel sppTargetLbl = new JLabel("SPP Target: ");
		sppTargetLbl.setToolTipText("The render will be paused at this SPP count");
		
		JButton setDefaultBtn = new JButton("Set Default");
		setDefaultBtn.setToolTipText("Make the current SPP target the default");
		setDefaultBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				ProgramProperties.setProperty("sppTargetDefault",
						"" + renderManager.scene().getTargetSPP());
				ProgramProperties.saveProperties();
			}
		});
		
		sppTargetField = new JTextField(10);
		sppTargetField.getDocument().addDocumentListener(sppTargetListener);
		
		updateSPPTargetField();
		
		JLabel renderLbl = new JLabel("Render: ");
		
		showPreviewBtn = new JButton();
		setViewVisible(false);
		showPreviewBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (view.isVisible()) {
					view.setVisible(false);
				} else {
					show3DView();
				}
			}
		});
		
		startRenderBtn.setText("START");
		startRenderBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				if (!renderManager.scene().pathTrace()) {
					renderManager.scene().startRender();
					startRenderBtn.setText("PAUSE");
					startRenderBtn.repaint();
				} else {
					if (renderManager.scene().isPaused()) {
						renderManager.scene().resumeRender();
						startRenderBtn.setText("PAUSE");
						startRenderBtn.repaint();
					} else {
						renderManager.scene().pauseRender();
						startRenderBtn.setText("RESUME");
						startRenderBtn.repaint();
					}
				}
				stopRenderBtn.setEnabled(true);
			}
		});
		
		stopRenderBtn.setText("HALT");
		stopRenderBtn.setToolTipText("<html>Warning: this will discard the " +
				"current rendered image!<br>Make sure to save your image " +
				"before stopping the renderer!");
		stopRenderBtn.setForeground(Color.red);
		stopRenderBtn.setEnabled(false);
		stopRenderBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				renderManager.scene().haltRender();
				startRenderBtn.setText("START");
				startRenderBtn.repaint();
				stopRenderBtn.setEnabled(false);
			}
		});
		
		saveFrameBtn = new JButton("Save Current Frame");
		saveFrameBtn.addActionListener(saveFrameListener);
		
		renderTimeLbl = new JLabel();
		samplesPerSecondLbl = new JLabel();
		samplesPerSecondLbl.setToolTipText("Samples Per Second");
		sppLbl = new JLabel();
		sppLbl.setToolTipText("Samples Per Pixel");
		
		setRenderTime(0);
		setSamplesPerSecond(0);
		setSPP(0);
		setProgress("Progress:", 0, 0, 1);
		
		progressBar = new JProgressBar();
		
		progressLbl = new JLabel("Progress:");
		
		etaLbl = new JLabel("ETA:");
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addGroup(layout.createParallelGroup()
				.addComponent(tabbedPane)
				.addComponent(showPreviewBtn)
				.addGroup(layout.createSequentialGroup()
					.addComponent(sppTargetLbl)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(sppTargetField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(setDefaultBtn)
				)
				.addGroup(layout.createSequentialGroup()
					.addComponent(renderLbl)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(startRenderBtn)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(stopRenderBtn)
					.addPreferredGap(ComponentPlacement.UNRELATED)
					.addComponent(saveFrameBtn)
				)
				.addComponent(renderTimeLbl)
				.addComponent(samplesPerSecondLbl)
				.addComponent(sppLbl)
				.addGroup(layout.createSequentialGroup()
					.addComponent(progressLbl)
					.addPreferredGap(ComponentPlacement.UNRELATED, GroupLayout.PREFERRED_SIZE, Short.MAX_VALUE)
					.addComponent(etaLbl)
				)
				.addComponent(progressBar))
			.addContainerGap()
		);
		layout.setVerticalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addComponent(tabbedPane)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(showPreviewBtn)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addGroup(layout.createParallelGroup(Alignment.BASELINE)
				.addComponent(sppTargetLbl)
				.addComponent(sppTargetField)
				.addComponent(setDefaultBtn)
			)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addGroup(layout.createParallelGroup(Alignment.BASELINE)
				.addComponent(renderLbl)
				.addComponent(startRenderBtn)
				.addComponent(stopRenderBtn)
				.addComponent(saveFrameBtn)
			)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(renderTimeLbl)
			.addComponent(samplesPerSecondLbl)
			.addComponent(sppLbl)
			.addPreferredGap(ComponentPlacement.RELATED)
			.addGroup(layout.createParallelGroup()
				.addComponent(progressLbl)
				.addComponent(etaLbl)
			)
			.addComponent(progressBar)
			.addContainerGap()
		);
		setContentPane(panel);
		
		pack();
		
		setLocationRelativeTo(chunky.getFrame());
		
		setVisible(true);
	}

	private Component buildAdvancedPane() {
		JLabel gammaLbl = new JLabel("gamma: ");

		gammaField = new JTextField(5);
		gammaField.addActionListener(gammaFieldListener);
		updateGammaField();

		gammaSlider = new JSlider(1, 100);
		gammaSlider.addChangeListener(gammaListener);
		updateGammaSlider();

		JLabel rayDepthLbl = new JLabel("Ray depth: ");
		rayDepthField = new JTextField(5);
		rayDepthField.addActionListener(rayDepthFieldListener);
		updateRayDepthField();
		rayDepthSlider = new JSlider(1, 10);
		rayDepthSlider.addChangeListener(rayDepthListener);
		updateRayDepthSlider();
		
		JSeparator sep1 = new JSeparator();
		
		JLabel waterWorldLbl = new JLabel("Note: Chunks must be reloaded after toggling the water world mode!");
		JLabel waterHeightLbl = new JLabel("Water height: ");
		waterHeightField = new JTextField(5);
		waterHeightField.setText("" + World.SEA_LEVEL);
		waterHeightField.setEnabled(false);
		waterHeightField.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JTextField source = (JTextField) e.getSource();
				renderManager.scene().setWaterHeight(Integer.parseInt(source.getText()));
				updateWaterHeight();
			}
		});
		
		waterWorldCB = new JCheckBox("Water World Mode");
		waterWorldCB.setSelected(false);
		waterWorldCB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JCheckBox source = (JCheckBox) e.getSource();
				if (source.isSelected()) {
					renderManager.scene().setWaterHeight(
							Integer.parseInt(waterHeightField.getText()));
				} else {
					renderManager.scene().setWaterHeight(0);
				}
				updateWaterHeight();
			}
		});
		
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addGroup(layout.createParallelGroup()
				.addGroup(layout.createSequentialGroup()
					.addComponent(rayDepthLbl)
					.addComponent(rayDepthSlider)
					.addComponent(rayDepthField))
				.addGroup(layout.createSequentialGroup()
					.addComponent(gammaLbl)
					.addComponent(gammaSlider)
					.addComponent(gammaField))
				.addComponent(sep1)
				.addComponent(waterWorldLbl)
				.addComponent(waterWorldCB)
				.addGroup(layout.createSequentialGroup()
					.addComponent(waterHeightLbl)
					.addGap(0, 0, Short.MAX_VALUE)
					.addComponent(waterHeightField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			)
			.addContainerGap()
		);
		layout.setVerticalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addGroup(layout.createParallelGroup()
				.addComponent(rayDepthLbl)
				.addComponent(rayDepthSlider)
				.addComponent(rayDepthField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addGroup(layout.createParallelGroup()
				.addComponent(gammaLbl)
				.addComponent(gammaSlider)
				.addComponent(gammaField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(sep1, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, GroupLayout.PREFERRED_SIZE)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(waterWorldLbl)
			.addPreferredGap(ComponentPlacement.RELATED)
			.addComponent(waterWorldCB)
			.addPreferredGap(ComponentPlacement.RELATED)
			.addGroup(layout.createParallelGroup()
				.addComponent(waterHeightLbl, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
				.addComponent(waterHeightField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addContainerGap()
		);
		return panel;
	}

	private Component buildGeneralPane() {
		JLabel widthLbl = new JLabel("Canvas width: ");
		JLabel heightLbl = new JLabel("Canvas height: ");
		
		widthField = new JTextField(10);
		widthField.addActionListener(canvasSizeListener);
		heightField = new JTextField(10);
		heightField.addActionListener(canvasSizeListener);
		
		updateWidthField();
		updateHeightField();
		
		saveSceneBtn = new JButton("Save Scene");
		saveSceneBtn.addActionListener(saveSceneListener);
		
		loadSceneBtn = new JButton("Load Scene");
		loadSceneBtn.addActionListener(loadSceneListener);
		
		JButton loadSelectedChunksBtn = new JButton("Load Selected Chunks");
		loadSelectedChunksBtn.setToolTipText("Load the chunks that are currently selected in the map view");
		loadSelectedChunksBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				loadChunks(chunky.getWorld(), chunky.getSelectedChunks());
			}
		});
		
		JButton reloadChunksBtn = new JButton("Reload Chunks");
		reloadChunksBtn.setToolTipText("Reload all chunks in the scene");
		reloadChunksBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				sceneManager.reloadChunks();
			}
		});
		
		JButton openSceneDirBtn = new JButton("Open Scene Directory");
		openSceneDirBtn.setToolTipText("Open the directory where Chunky stores scene descriptions and renders");
		openSceneDirBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				try {
					Desktop.getDesktop().open(context.getSceneDirectory());
				} catch (IOException e) {
					logger.warn("Failed to open scene directory", e);
				}
			}
		});
		
		loadSceneBtn.setToolTipText("This replaces the current scene!");
		JButton setCanvasSizeBtn = new JButton("Set Canvas Size");
		setCanvasSizeBtn.addActionListener(canvasSizeListener);
		JButton halveCanvasSizeBtn = new JButton("Halve");
		halveCanvasSizeBtn.setToolTipText("Halve the canvas size");
		halveCanvasSizeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int width = renderManager.scene().canvasWidth() / 2;
				int height = renderManager.scene().canvasHeight() / 2;
				setCanvasSize(width, height);
			}
		});
		JButton doubleCanvasSizeBtn = new JButton("Double");
		halveCanvasSizeBtn.setToolTipText("Double the canvas size");
		doubleCanvasSizeBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				int width = renderManager.scene().canvasWidth() * 2;
				int height = renderManager.scene().canvasHeight() * 2;
				setCanvasSize(width, height);
			}
		});
		
		JButton makeDefaultBtn = new JButton("Make Default");
		makeDefaultBtn.setToolTipText("Make the current canvas size the default");
		makeDefaultBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				ProgramProperties.setProperty("3dcanvas.width",
						"" + renderManager.scene().canvasWidth());
				ProgramProperties.setProperty("3dcanvas.height",
						"" + renderManager.scene().canvasHeight());
				ProgramProperties.saveProperties();
			}
		});
		
		JSeparator sep1 = new JSeparator();
		JSeparator sep2 = new JSeparator();
		
		sceneNameLbl = new JLabel("Scene name: ");
		sceneNameField = new JTextField(15); 
		sceneNameField.getDocument().addDocumentListener(sceneNameListener);
		updateSceneNameField();
		
		stillWaterCB = new JCheckBox("still water");
		stillWaterCB.addActionListener(stillWaterListener);
		updateStillWater();
		
		clearWaterCB = new JCheckBox("clear water");
		stillWaterCB.addActionListener(clearWaterListener);
		updateClearWater();
		
		biomeColorsCB = new JCheckBox("enable biome colors");
		updateBiomeColorsCB();
		
		JCheckBox gammaCorrectionCB = new JCheckBox("enable gamma correction");
		gammaCorrectionCB.setSelected(renderManager.scene().getGammaCorrectionEnabled());
		gammaCorrectionCB.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				JCheckBox source = (JCheckBox) e.getSource();
				renderManager.scene().setGammaCorrectionEnabled(source.isSelected());
			}
		});
		
		saveDumpsCB = new JCheckBox("save dump once every ");
		saveDumpsCB.addActionListener(saveDumpsListener);
		updateSaveDumpsCheckBox();
		
		JLabel dumpFrequencyLbl = new JLabel(" frame");
		String[] frequencyStrings = new String[dumpFrequencies.length];
		for (int i = 0; i < dumpFrequencies.length; ++i)
			frequencyStrings[i] = dumpFrequencies[i] + "th";
		dumpFrequency = new JComboBox(frequencyStrings);
		dumpFrequency.setEditable(false);
		dumpFrequency.addActionListener(dumpFrequencyListener);
		updateDumpFrequencyField();
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(
				layout.createSequentialGroup()
					.addContainerGap()
					.addGroup(layout.createParallelGroup()
						.addGroup(layout.createSequentialGroup()
							.addComponent(sceneNameLbl)
							.addComponent(sceneNameField))
						.addGroup(layout.createSequentialGroup()
							.addComponent(saveSceneBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(loadSceneBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(openSceneDirBtn))
						.addGroup(layout.createSequentialGroup()
							.addComponent(loadSelectedChunksBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(reloadChunksBtn))
						.addComponent(sep1)
						.addGroup(layout.createSequentialGroup()
							.addGroup(layout.createParallelGroup()
								.addComponent(widthLbl)
								.addComponent(heightLbl))
							.addGroup(layout.createParallelGroup()
								.addComponent(widthField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
								.addComponent(heightField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)))
						.addGroup(layout.createSequentialGroup()
							.addComponent(setCanvasSizeBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(halveCanvasSizeBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(doubleCanvasSizeBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(makeDefaultBtn))
						.addComponent(sep2)
						.addComponent(gammaCorrectionCB)
						.addComponent(stillWaterCB)
						.addComponent(clearWaterCB)
						.addComponent(biomeColorsCB)
						.addGroup(layout.createSequentialGroup()
							.addComponent(saveDumpsCB)
							.addComponent(dumpFrequency, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE)
							.addComponent(dumpFrequencyLbl)
							.addGap(0, 0, Short.MAX_VALUE)))
					.addContainerGap());
		layout.setVerticalGroup(
			layout.createSequentialGroup()
				.addContainerGap()
				.addGroup(layout.createParallelGroup(Alignment.BASELINE)
					.addComponent(sceneNameLbl)
					.addComponent(sceneNameField))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(saveSceneBtn)
					.addComponent(loadSceneBtn)
					.addComponent(openSceneDirBtn))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(loadSelectedChunksBtn)
					.addComponent(reloadChunksBtn))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(sep1)
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addGroup(layout.createParallelGroup(Alignment.BASELINE)
					.addComponent(widthLbl)
					.addComponent(widthField))
				.addGroup(layout.createParallelGroup(Alignment.BASELINE)
					.addComponent(heightLbl)
					.addComponent(heightField))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(setCanvasSizeBtn)
					.addComponent(halveCanvasSizeBtn)
					.addComponent(doubleCanvasSizeBtn)
					.addComponent(makeDefaultBtn))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(sep2)
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(gammaCorrectionCB)
				.addComponent(stillWaterCB)
				.addComponent(clearWaterCB)
				.addComponent(biomeColorsCB)
				.addGroup(layout.createParallelGroup(Alignment.BASELINE)
					.addComponent(saveDumpsCB)
					.addComponent(dumpFrequency)
					.addComponent(dumpFrequencyLbl))
				.addContainerGap());
		return panel;
	}
	
	private Component buildLightingPane() {
		
		JButton changeSunColorBtn = new JButton("Change Sun Color");
		changeSunColorBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				java.awt.Color newColor = JColorChooser.showDialog(
						RenderControls.this, "Choose Sun Color",
						renderManager.scene().sun().getAwtColor());
				if (newColor != null)
					renderManager.scene().sun().setColor(newColor);
			}
		});
		
		directLight = new JCheckBox("enable sunlight");
		directLight.setSelected(renderManager.scene().getDirectLight());
		directLight.addActionListener(directLightListener);
		
		enableEmitters = new JCheckBox("enable emitters");
		enableEmitters.setSelected(renderManager.scene().getEmittersEnabled());
		enableEmitters.addActionListener(emittersListener);
		
		JLabel sunYawLbl = new JLabel("Sun yaw: ");
		JLabel sunPitchLbl = new JLabel("Sun pitch: ");

		JLabel emitterIntensityLbl = new JLabel("Emitter intensity: ");
		emitterIntensitySlider = new JSlider(1, 100);
		emitterIntensitySlider.addChangeListener(emitterIntensityListener);
		updateEmitterIntensitySlider();
		
		JLabel sunIntensityLbl = new JLabel("Sun intensity: ");
		sunIntensitySlider = new JSlider(0, 100);
		sunIntensitySlider.addChangeListener(sunIntensityListener);
		updateSunIntensitySlider();
		
		sunYawSlider = new JSlider(0, 100);
		sunYawSlider.addChangeListener(sunYawListener);
		
		sunPitchSlider = new JSlider(0, 100);
		sunPitchSlider.addChangeListener(sunPitchListener);
		
		updateSunYawSlider();
		updateSunPitchSlider();
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addGroup(layout.createParallelGroup()
				.addComponent(directLight)
				.addComponent(enableEmitters)
				.addGroup(layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup()
						.addComponent(emitterIntensityLbl)
						.addComponent(sunIntensityLbl)
						.addComponent(sunYawLbl)
						.addComponent(sunPitchLbl))
					.addGroup(layout.createParallelGroup()
						.addComponent(emitterIntensitySlider)
						.addComponent(sunIntensitySlider)
						.addComponent(sunYawSlider)
						.addComponent(sunPitchSlider)))
				.addComponent(changeSunColorBtn)
			)
			.addContainerGap()
		);
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addContainerGap()
				.addComponent(enableEmitters)
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(emitterIntensityLbl)
					.addComponent(emitterIntensitySlider))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(directLight)
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(sunIntensityLbl)
					.addComponent(sunIntensitySlider))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(sunYawLbl)
					.addComponent(sunYawSlider))
				.addPreferredGap(ComponentPlacement.RELATED)
				.addGroup(layout.createParallelGroup()
					.addComponent(sunPitchLbl)
					.addComponent(sunPitchSlider))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(changeSunColorBtn)
				.addContainerGap());
		return panel;
	}
	
	private Component buildSkyPane() {
		
		JLabel skyRotationLbl = new JLabel("Skymap rotation:");
		skyRotation = new JSlider(0, 100);
		skyRotation.addChangeListener(skyRotationListener);
		updateSkyRotation();
		
		loadSkymapBtn = new JButton("Load Skymap");
		loadSkymapBtn.setToolTipText("Use a panoramic skymap");
		loadSkymapBtn.addActionListener(loadSkymapListener);
		
		JButton unloadSkymapBtn = new JButton("Unload Skymap");
		unloadSkymapBtn.setToolTipText("Use the default sky");
		unloadSkymapBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				renderManager.scene().sky().unloadSkymap();
			}
		});
		
		atmosphereEnabled = new JCheckBox("enable atmosphere");
		atmosphereEnabled.addActionListener(atmosphereListener);
		updateAtmosphereCheckBox();
		
		volumetricFogEnabled = new JCheckBox("enable volumetric fog");
		volumetricFogEnabled.addActionListener(volumetricFogListener);
		updateVolumetricFogCheckBox();
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(
				layout.createSequentialGroup()
					.addContainerGap()
					.addGroup(layout.createParallelGroup()
						.addGroup(layout.createSequentialGroup()
							.addComponent(skyRotationLbl)
							.addComponent(skyRotation))
						.addGroup(layout.createSequentialGroup()
							.addComponent(loadSkymapBtn)
							.addPreferredGap(ComponentPlacement.RELATED)
							.addComponent(unloadSkymapBtn))
						.addComponent(atmosphereEnabled)
						.addComponent(volumetricFogEnabled))
					.addContainerGap());
		layout.setVerticalGroup(
			layout.createSequentialGroup()
				.addContainerGap()
				.addGroup(layout.createParallelGroup()
					.addComponent(loadSkymapBtn)
					.addComponent(unloadSkymapBtn))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addGroup(layout.createParallelGroup(Alignment.BASELINE)
					.addComponent(skyRotationLbl)
					.addComponent(skyRotation))
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(atmosphereEnabled)
				.addPreferredGap(ComponentPlacement.UNRELATED)
				.addComponent(volumetricFogEnabled)
				.addContainerGap());
		return panel;
	}
	
	private Component buildCameraPane() {
		
		JLabel fovLbl = new JLabel("Field of View (zoom): ");
		JLabel dofLbl = new JLabel("Depth of Field: ");
		JLabel focalOffsetLbl = new JLabel("Focal Offset: ");

		dofSlider = new JSlider(1, 1000);
		dofSlider.addChangeListener(dofListener);
		updateDofSlider();
		
		fovSlider = new JSlider(1, 1000);
		fovSlider.addChangeListener(fovListener);
		updateFovSlider();
		
		focalOffsetSlider = new JSlider(1, 1000);
		focalOffsetSlider.addChangeListener(focalOffsetListener);
		updateFocalOffsetSlider();
		
		fovField = new JTextField(5);
		fovField.addActionListener(fovFieldListener);
		updateFovField();
		
		dofField = new JTextField(5);
		dofField.addActionListener(dofFieldListener);
		updateDofField();
		
		focalOffsetField = new JTextField(5);
		focalOffsetField.addActionListener(focalOffsetFieldListener);
		updateFocalOffsetField();
		
		JButton autoFocusBtn = new JButton("Autofocus");
		autoFocusBtn.setToolTipText("Focuses on the object right in the center, under the crosshairs");
		autoFocusBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				renderManager.scene().autoFocus();
				updateDofField();
				updateDofSlider();
				updateFocalOffsetField();
				updateFocalOffsetSlider();
			}
		});
		
		JButton cameraToPlayerBtn = new JButton("Camera to player");
		cameraToPlayerBtn.setToolTipText("Move camera to player position");
		cameraToPlayerBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				renderManager.scene().moveCameraToPlayer();
			}
		});
		
		JButton xposBtn = new JButton("+X");
		xposBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(Math.PI, -Math.PI/2);
			}
		});
		
		JButton xnegBtn = new JButton("-X");
		xnegBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(0, -Math.PI/2);
			}
		});
		
		JButton yposBtn = new JButton("+Y");
		yposBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(-Math.PI/2, Math.PI);
			}
		});
		
		JButton ynegBtn = new JButton("-Y");
		ynegBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(-Math.PI/2, 0);
			}
		});
		
		JButton zposBtn = new JButton("+Z");
		zposBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(Math.PI/2, -Math.PI/2);
			}
		});
		
		JButton znegBtn = new JButton("-Z");
		znegBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				Camera camera = renderManager.scene().camera();
				camera.setFoV(90);
				camera.setView(-Math.PI/2, -Math.PI/2);
			}
		});
		
		JButton centerCameraBtn = new JButton("Center camera");
		centerCameraBtn.setToolTipText("Center camera above loaded chunks");
		centerCameraBtn.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				renderManager.scene().moveCameraToCenter();
			}
		});
		
		JSeparator sep1 = new JSeparator();
		
		JPanel panel = new JPanel();
		GroupLayout layout = new GroupLayout(panel);
		panel.setLayout(layout);
		layout.setHorizontalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addGroup(layout.createParallelGroup()
				.addComponent(cameraToPlayerBtn)
				.addComponent(centerCameraBtn)
				.addGroup(layout.createSequentialGroup()
					.addComponent(xposBtn)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(xnegBtn)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(yposBtn)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(ynegBtn)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(zposBtn)
					.addPreferredGap(ComponentPlacement.RELATED)
					.addComponent(znegBtn)
				)
				.addComponent(sep1)
				.addGroup(layout.createSequentialGroup()
					.addGroup(layout.createParallelGroup()
						.addComponent(fovLbl)
						.addComponent(dofLbl)
						.addComponent(focalOffsetLbl))
					.addGroup(layout.createParallelGroup()
						.addComponent(fovSlider)
						.addComponent(dofSlider)
						.addComponent(focalOffsetSlider))
					.addGroup(layout.createParallelGroup()
						.addComponent(fovField)
						.addComponent(dofField)
						.addComponent(focalOffsetField)))
				.addComponent(autoFocusBtn))
			.addContainerGap()
		);
		layout.setVerticalGroup(layout.createSequentialGroup()
			.addContainerGap()
			.addComponent(cameraToPlayerBtn)
			.addPreferredGap(ComponentPlacement.RELATED)
			.addComponent(centerCameraBtn)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addGroup(layout.createParallelGroup()
				.addComponent(xposBtn)
				.addComponent(xnegBtn)
				.addComponent(yposBtn)
				.addComponent(ynegBtn)
				.addComponent(zposBtn)
				.addComponent(znegBtn)
			)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(sep1)
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addGroup(layout.createParallelGroup()
				.addComponent(fovLbl)
				.addComponent(fovSlider)
				.addComponent(fovField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addGroup(layout.createParallelGroup()
				.addComponent(dofLbl)
				.addComponent(dofSlider)
				.addComponent(dofField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addGroup(layout.createParallelGroup()
				.addComponent(focalOffsetLbl)
				.addComponent(focalOffsetSlider)
				.addComponent(focalOffsetField, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE, GroupLayout.PREFERRED_SIZE))
			.addPreferredGap(ComponentPlacement.UNRELATED)
			.addComponent(autoFocusBtn)
			.addContainerGap(GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
		);
		return panel;
	}

	protected void updateStillWater() {
		stillWaterCB.removeActionListener(stillWaterListener);
		stillWaterCB.setSelected(renderManager.scene().stillWaterEnabled());
		stillWaterCB.addActionListener(stillWaterListener);
	}
	
	protected void updateClearWater() {
		clearWaterCB.removeActionListener(clearWaterListener);
		clearWaterCB.setSelected(renderManager.scene().getClearWater());
		clearWaterCB.addActionListener(clearWaterListener);
	}
	
	protected void updateBiomeColorsCB() {
		biomeColorsCB.removeActionListener(biomeColorsCBListener);
		biomeColorsCB.addActionListener(biomeColorsCBListener);
		biomeColorsCB.setSelected(renderManager.scene().biomeColorsEnabled());
	}
	
	protected void updateAtmosphereCheckBox() {
		atmosphereEnabled.removeActionListener(atmosphereListener);
		atmosphereEnabled.setSelected(renderManager.scene().atmosphereEnabled());
		atmosphereEnabled.addActionListener(atmosphereListener);
	}

	protected void updateVolumetricFogCheckBox() {
		volumetricFogEnabled.removeActionListener(volumetricFogListener);
		volumetricFogEnabled.addActionListener(volumetricFogListener);
		volumetricFogEnabled.setSelected(renderManager.scene().volumetricFogEnabled());
	}
	
	private void updateTitle() {
		setTitle("Render Controls - " + renderManager.scene().name());
	}
	
	ActionListener dumpFrequencyListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				int index = dumpFrequency.getSelectedIndex();
				index = Math.max(0, index);
				index = Math.min(dumpFrequencies.length-1, index);
				renderManager.scene().setDumpFrequency(dumpFrequencies[index]);
			} catch (NumberFormatException e1) {
			}
			updateDumpFrequencyField();
		}
	};
	
	ActionListener saveDumpsListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			boolean enabled = saveDumpsCB.isSelected();
			renderManager.scene().setSaveDumps(enabled);
			dumpFrequency.setEnabled(enabled);
		}
	};

	ActionListener canvasSizeListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			try {
				int width = Integer.parseInt(widthField.getText());
				int height = Integer.parseInt(heightField.getText());
				setCanvasSize(width, height);
			} catch (NumberFormatException e1) {
				logger.info("Failed to set canvas size: invalid dimensions!");
			}
		}
	};
	
	DocumentListener sceneNameListener = new DocumentListener() {
		@Override
		public void removeUpdate(DocumentEvent e) {
			updateName(e);
		}
		@Override
		public void insertUpdate(DocumentEvent e) {
			updateName(e);
		}
		@Override
		public void changedUpdate(DocumentEvent e) {
			updateName(e);
		}
		private void updateName(DocumentEvent e) {
			try {
				Document d = e.getDocument();
				renderManager.scene().setName(d.getText(0, d.getLength()));
				updateTitle();
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}
	};
	
	DocumentListener sppTargetListener = new DocumentListener() {
		@Override
		public void removeUpdate(DocumentEvent e) {
			updateName(e);
		}
		@Override
		public void insertUpdate(DocumentEvent e) {
			updateName(e);
		}
		@Override
		public void changedUpdate(DocumentEvent e) {
			updateName(e);
		}
		private void updateName(DocumentEvent e) {
			try {
				Document d = e.getDocument();
				String value = d.getText(0, d.getLength());
				renderManager.scene().setTargetSPP(Integer.parseInt(value));
				updateTitle();
			} catch (NumberFormatException e1) {
			} catch (BadLocationException e1) {
				e1.printStackTrace();
			}
		}
	};
	
	ActionListener saveSceneListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			sceneManager.saveScene(sceneNameField.getText());
		}
	};
	ActionListener saveFrameListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			renderManager.saveFrame();
		}
	};
	ActionListener loadSceneListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			new SceneSelector(RenderControls.this, context);
		}
	};
	ActionListener loadSkymapListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			CenteredFileDialog fileDialog =
					new CenteredFileDialog(null, "Open Skymap", FileDialog.LOAD);
			fileDialog.setDirectory(System.getProperty("user.dir"));
			fileDialog.setFilenameFilter(
					new FilenameFilter() {
						@Override
						public boolean accept(File dir, String name) {
							return name.toLowerCase().endsWith(".png")
									|| name.toLowerCase().endsWith(".jpg");
						}
					});
			fileDialog.setVisible(true);
			File selectedFile = fileDialog.getSelectedFile();
			if (selectedFile != null) {
				renderManager.scene().sky().loadSkyMap(selectedFile.getAbsolutePath());
			}
		}
	};
	ChangeListener rayDepthListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			renderManager.scene().setRayDepth(source.getValue());
			updateRayDepthField();
		}
	};
	ActionListener rayDepthFieldListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JTextField source = (JTextField) e.getSource();
			try {
				int value = Integer.parseInt(source.getText());
				renderManager.scene().setRayDepth(Math.max(1, value));
				updateRayDepthSlider();
			} catch (NumberFormatException ex) {
			}
		}
	};
	ChangeListener emitterIntensityListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			renderManager.scene().setEmitterIntensity(source.getValue()*2);
		}
	};
	ChangeListener sunIntensityListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			double value = (double) (source.getValue() - source.getMinimum()) /
					(source.getMaximum() - source.getMinimum());
			double scale = Sun.MAX_INTENSITY - Sun.MIN_INTENSITY;
			renderManager.scene().sun().setIntensity(value * scale + Sun.MIN_INTENSITY);
		}
	};
	ChangeListener sunYawListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			renderManager.scene().sun().setPolarAngle(source.getValue() * Math.PI / 50);
		}
	};
	ChangeListener sunPitchListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			renderManager.scene().sun().setTheta(source.getValue() * Math.PI / 200);
		}
	};
	ChangeListener dofListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			if (source.getValue() == source.getMaximum()) {
				renderManager.scene().camera().setInfDof(true);
			} else {
				renderManager.scene().camera().setInfDof(false);
				double value = (double) (source.getValue() - source.getMinimum())
						/ (source.getMaximum() - source.getMinimum());
				double scale = Camera.MAX_DOF - Camera.MIN_DOF;
				renderManager.scene().camera().setDof(value * scale + Camera.MIN_DOF);
			}
			updateDofField();
		}
	};
	ChangeListener skyRotationListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			double value = (double) (source.getValue() - source.getMinimum())
					/ (source.getMaximum() - source.getMinimum());
			double rotation = value * 2 * Math.PI;
			renderManager.scene().sky().setRotation(rotation);
			ProgramProperties.setProperty("skymapRotation", Double.toString(rotation));
			updateDofField();
		}
	};
	ChangeListener fovListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			double value = (double) (source.getValue() - source.getMinimum())
					/ (source.getMaximum() - source.getMinimum());
			double scale = Camera.MAX_FOV - Camera.MIN_FOV;
			renderManager.scene().camera().setFoV(value * scale + Camera.MIN_FOV);
			updateFovField();
		}
	};
	ChangeListener gammaListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			double value = (double) (source.getValue() - source.getMinimum())
					/ (source.getMaximum() - source.getMinimum());
			double scale = Scene.MAX_GAMMA
				- Scene.MIN_GAMMA;
			renderManager.scene().setGamma(
					value * scale + Scene.MIN_GAMMA);
			updateGammaField();
		}
	};
	ActionListener gammaFieldListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JTextField source = (JTextField) e.getSource();
			try {
				double value = numberFormat.parse(source.getText()).doubleValue();
				value = Math.max(value, Scene.MIN_GAMMA);
				value = Math.min(value, Scene.MAX_GAMMA);
				renderManager.scene().setGamma(value);
				updateGammaSlider();
			} catch (NumberFormatException ex) {
			} catch (ParseException ex) {
			}
		}
	};
	ChangeListener focalOffsetListener = new ChangeListener() {
		@Override
		public void stateChanged(ChangeEvent e) {
			JSlider source = (JSlider) e.getSource();
			double value = (double) (source.getValue() - source.getMinimum())
					/ (source.getMaximum() - source.getMinimum());
			double scale = Camera.MAX_FOCAL_OFFSET - Camera.MIN_FOCAL_OFFSET;
			renderManager.scene().camera().setFocalOffset(value * scale + Camera.MIN_FOCAL_OFFSET);
			updateFocalOffsetField();
		}
	};
	ActionListener stillWaterListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			renderManager.scene().setStillWater(stillWaterCB.isSelected());
		}
	};
	ActionListener clearWaterListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			renderManager.scene().setClearWater(clearWaterCB.isSelected());
		}
	};
	ActionListener atmosphereListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JCheckBox source = (JCheckBox) e.getSource();
			renderManager.scene().setAtmosphereEnabled(source.isSelected());
		}
	};
	ActionListener volumetricFogListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JCheckBox source = (JCheckBox) e.getSource();
			renderManager.scene().setVolumetricFogEnabled(source.isSelected());
		}
	};
	ActionListener biomeColorsCBListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JCheckBox source = (JCheckBox) e.getSource();
			renderManager.scene().setBiomeColorsEnabled(source.isSelected());
		}
	};
	ActionListener emittersListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			renderManager.scene().setEmittersEnabled(enableEmitters.isSelected());
		}
	};
	ActionListener directLightListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			renderManager.scene().setDirectLight(directLight.isSelected());
		}
	};
	ActionListener fovFieldListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JTextField source = (JTextField) e.getSource();
			try {
				double value = numberFormat.parse(source.getText()).doubleValue();
				value = Math.max(value, Camera.MIN_FOV);
				value = Math.min(value, Camera.MAX_FOV);
				renderManager.scene().camera().setFoV(value);
				updateFovSlider();
			} catch (NumberFormatException ex) {
			} catch (ParseException ex) {
			}
		}
	};
	ActionListener dofFieldListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JTextField source = (JTextField) e.getSource();
			if (source.getText().equals("inf")) {
				renderManager.scene().camera().setInfDof(true);
			} else {
				try {
					double value = numberFormat.parse(source.getText()).doubleValue();
					value = Math.max(value, Camera.MIN_DOF);
					renderManager.scene().camera().setInfDof(false);
					renderManager.scene().camera().setDof(value);
					updateDofSlider();
				} catch (NumberFormatException ex) {
				} catch (ParseException ex) {
				}
			}
		}
	};
	ActionListener focalOffsetFieldListener = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent e) {
			JTextField source = (JTextField) e.getSource();
			try {
				double value = numberFormat.parse(source.getText()).doubleValue();
				value = Math.max(value, Camera.MIN_FOCAL_OFFSET);
				renderManager.scene().camera().setFocalOffset(value);
				updateFocalOffsetSlider();
			} catch (NumberFormatException ex) {
			} catch (ParseException ex) {
			}
		}
	};
	
	protected void updateWaterHeight() {
		int height = renderManager.scene().getWaterHeight();
		boolean waterWorld = height > 0;
		if (waterWorld) {
			waterHeightField.setText("" + height);
		}
		waterWorldCB.setSelected(height > 0);
		waterHeightField.setEnabled(height > 0);
	}
	
	protected void updateRayDepthSlider() {
		rayDepthSlider.removeChangeListener(rayDepthListener);
		rayDepthSlider.setValue(renderManager.scene().getRayDepth());
		rayDepthSlider.addChangeListener(rayDepthListener);
	}
	
	protected void updateRayDepthField() {
		rayDepthField.removeActionListener(rayDepthFieldListener);
		rayDepthField.setText("" + renderManager.scene().getRayDepth());
		rayDepthField.addActionListener(rayDepthFieldListener);
	}
	
	protected void updateDofField() {
		dofField.removeActionListener(dofFieldListener);
		if (renderManager.scene().camera().getInfDof())
			dofField.setText("inf");
		else
			dofField.setText(String.format("%.2f", renderManager.scene().camera().getDof()));
		dofField.addActionListener(dofFieldListener);
	}
	
	protected void updateFocalOffsetField() {
		focalOffsetField.removeActionListener(focalOffsetFieldListener);
		focalOffsetField.setText(String.format("%.2f", renderManager.scene().camera().getFocalOffset()));
		focalOffsetField.addActionListener(focalOffsetFieldListener);
	}

	protected void updateFovField() {
		fovField.removeActionListener(fovFieldListener);
		fovField.setText(String.format("%.2f", renderManager.scene().camera().getFoV()));
		fovField.addActionListener(fovFieldListener);
	}
	
	protected void updateSkyRotation() {
		skyRotation.removeChangeListener(skyRotationListener);
		skyRotation.setValue((int) Math.round(
				100 * renderManager.scene().sky().getRotation() / (2 * Math.PI)));
		skyRotation.addChangeListener(skyRotationListener);
	}
	
	protected void updateSunPitchSlider() {
		sunPitchSlider.removeChangeListener(sunPitchListener);
		sunPitchSlider.setValue((int) (renderManager.scene().sun().getTheta() * 200 / Math.PI));
		sunPitchSlider.addChangeListener(sunPitchListener);
	}
	
	protected void updateEmitterIntensitySlider() {
		emitterIntensitySlider.removeChangeListener(emitterIntensityListener);
		emitterIntensitySlider.setValue((int) (renderManager.scene().getEmitterIntensity()/2));
		emitterIntensitySlider.addChangeListener(emitterIntensityListener);
	}
	
	protected void updateSunIntensitySlider() {
		sunIntensitySlider.removeChangeListener(sunIntensityListener);
		double value = (renderManager.scene().sun().getIntensity() - Sun.MIN_INTENSITY) /
				(Sun.MAX_INTENSITY - Sun.MIN_INTENSITY);
		double scale = sunIntensitySlider.getMaximum() - sunIntensitySlider.getMinimum();
		sunIntensitySlider.setValue((int) (value * scale + sunIntensitySlider.getMinimum()));
		sunIntensitySlider.addChangeListener(sunIntensityListener);
	}
	
	protected void updateSunYawSlider() {
		sunYawSlider.removeChangeListener(sunYawListener);
		sunYawSlider.setValue((int) (renderManager.scene().sun().getPolarAngle() * 50 / Math.PI));
		sunYawSlider.addChangeListener(sunYawListener);
	}
	
	protected void updateDofSlider() {
		dofSlider.removeChangeListener(dofListener);
		if (renderManager.scene().camera().getInfDof()) {
			dofSlider.setValue(dofSlider.getMaximum());
		} else {
			double value = (renderManager.scene().camera().getDof() - Camera.MIN_DOF)
					/ (Camera.MAX_DOF - Camera.MIN_DOF);
			double scale = dofSlider.getMaximum() - dofSlider.getMinimum();
			dofSlider.setValue((int) (value * scale + dofSlider.getMinimum()));
		}
		dofSlider.addChangeListener(dofListener);
	}
	
	protected void updateFocalOffsetSlider() {
		focalOffsetSlider.removeChangeListener(focalOffsetListener);
		double value = (renderManager.scene().camera().getFocalOffset() - Camera.MIN_FOCAL_OFFSET)
				/ (Camera.MAX_FOCAL_OFFSET - Camera.MIN_FOCAL_OFFSET);
		double scale = focalOffsetSlider.getMaximum() - focalOffsetSlider.getMinimum();
		focalOffsetSlider.setValue((int) (value * scale + focalOffsetSlider.getMinimum()));
		focalOffsetSlider.addChangeListener(focalOffsetListener);
	}

	protected void updateFovSlider() {
		fovSlider.removeChangeListener(fovListener);
		double value = (renderManager.scene().camera().getFoV() - Camera.MIN_FOV)
				/ (Camera.MAX_FOV - Camera.MIN_FOV);
		double scale = fovSlider.getMaximum() - fovSlider.getMinimum();
		fovSlider.setValue((int) (value * scale + fovSlider.getMinimum()));
		fovSlider.addChangeListener(fovListener);
	}

	protected void updateGammaField() {
		gammaField.removeActionListener(gammaFieldListener);
		gammaField.setText(String.format("%.2f", renderManager.scene().getGamma()));
		gammaField.addActionListener(gammaFieldListener);
	}
	
	protected void updateGammaSlider() {
		gammaSlider.removeChangeListener(gammaListener);
		double value = (renderManager.scene().getGamma()
				- Scene.MIN_GAMMA)
				/ (Scene.MAX_GAMMA - Scene.MIN_GAMMA);
		double scale = gammaSlider.getMaximum() - gammaSlider.getMinimum();
		gammaSlider.setValue((int) (value * scale + gammaSlider.getMinimum()));
		gammaSlider.addChangeListener(gammaListener);
	}

	protected void updateWidthField() {
		widthField.setText("" + renderManager.scene().canvasWidth());
	}
	
	protected void updateHeightField() {
		heightField.setText("" + renderManager.scene().canvasHeight());
	}
	
	protected void updateSaveDumpsCheckBox() {
		saveDumpsCB.removeActionListener(saveDumpsListener);
		saveDumpsCB.setSelected(renderManager.scene().saveDumps());
		saveDumpsCB.addActionListener(saveDumpsListener);
	}

	protected void updateDumpFrequencyField() {
		dumpFrequency.removeActionListener(dumpFrequencyListener);
		dumpFrequency.setEnabled(renderManager.scene().saveDumps());
		int frequency = renderManager.scene().getDumpFrequency();
		dumpFrequency.setSelectedIndex(0);
		for (int i = 0; i < dumpFrequencies.length; ++i) {
			if (frequency == dumpFrequencies[i]) {
				dumpFrequency.setSelectedIndex(i);
				break;
			}
		}
		dumpFrequency.addActionListener(dumpFrequencyListener);
	}
	
	protected void updateSceneNameField() {
		sceneNameField.getDocument().removeDocumentListener(sceneNameListener);
		sceneNameField.setText(renderManager.scene().name());
		sceneNameField.getDocument().addDocumentListener(sceneNameListener);
	}
	
	protected void updateSPPTargetField() {
		sppTargetField.getDocument().removeDocumentListener(sppTargetListener);
		sppTargetField.setText("" + renderManager.scene().getTargetSPP());
		sppTargetField.getDocument().addDocumentListener(sppTargetListener);
	}

	/**
	 * Load the scene with the given name
	 * @param sceneName The name of the scene to load
	 */
	public void loadScene(String sceneName) {
		sceneManager.loadScene(sceneName);
	}
	
	/**
	 * Called when the current scene has been saved
	 */
	public void sceneSaved() {
		updateTitle();
	}

	@Override
	public void onStrafeLeft() {
        renderManager.scene().camera().strafeLeft(
        		chunky.getShiftModifier() ? .1 : 1);
	}

	@Override
	public void onStrafeRight() {
        renderManager.scene().camera().strafeRight(
        		chunky.getShiftModifier() ? .1 : 1);
	}

	@Override
	public void onMoveForward() {
        renderManager.scene().camera().moveForward(
        		chunky.getShiftModifier() ? .1 : 1);
		
	}

	@Override
	public void onMoveBackward() {
        renderManager.scene().camera().moveBackward(
        		chunky.getShiftModifier() ? .1 : 1);
		
	}

	@Override
	public void onMoveForwardFar() {
	    renderManager.scene().camera().moveForward(100);
		
	}

	@Override
	public void onMoveBackwardFar() {
        renderManager.scene().camera().moveBackward(100);
		
	}

	@Override
	public void onMoveUp() {
        renderManager.scene().camera().moveUp(
        		chunky.getShiftModifier() ? .1 : 1);
		
	}

	@Override
	public void onMoveDown() {
        renderManager.scene().camera().moveDown(
        		chunky.getShiftModifier() ? .1 : 1);
		
	}

	@Override
	public void onMouseDragged(int dx, int dy) {
        renderManager.scene().camera().rotateView(
                - (Math.PI / 250) * dx,
                (Math.PI / 250) * dy);
		
	}

	/**
	 * Set the name of the current scene
	 * @param sceneName
	 */
	public void setSceneName(String sceneName) {
		renderManager.scene().setName(sceneName);
		sceneNameField.setText(renderManager.scene().name());
		updateTitle();
	}
	
	/**
	 * Load the given chunks.
	 * @param world
	 * @param chunks 
	 */
	public void loadChunks(World world, Collection<ChunkPosition> chunks) {
		sceneManager.loadChunks(world, chunks);
	}

	/**
	 * Update the Show/Hide 3D view button.
	 * @param visible
	 */
	@Override
	public void setViewVisible(boolean visible) {
		if (visible) {
			showPreviewBtn.setText("Hide Preview");
			showPreviewBtn.setToolTipText("Hide the preview window");
		} else {
			showPreviewBtn.setText("Show Preview");
			showPreviewBtn.setToolTipText("Show the preview window");
		}
	}
	
	protected void setCanvasSize(int width, int height) {
		renderManager.scene().setCanvasSize(width, height);
		int canvasWidth = renderManager.scene().canvasWidth();
		int canvasHeight = renderManager.scene().canvasHeight();
		widthField.setText("" + canvasWidth);
		heightField.setText("" + canvasHeight);
		view.setCanvasSize(canvasWidth, canvasHeight);
	}

	/**
	 * Method to notify the render controls dialog that a scene has been loaded.
	 * Causes canvas size to be updated.
	 */
	public synchronized void sceneLoaded() {
		updateDofField();
		updateFovField();
		updateFocalOffsetField();
		updateDofSlider();
		updateFovSlider();
		updateFocalOffsetSlider();
		updateWidthField();
		updateHeightField();
		updateEmitterIntensitySlider();
		updateSunIntensitySlider();
		updateSunYawSlider();
		updateSunPitchSlider();
		updateStillWater();
		updateClearWater();
		updateSkyRotation();
		updateBiomeColorsCB();
		updateAtmosphereCheckBox();
		updateVolumetricFogCheckBox();
		updateTitle();
		updateGammaField();
		updateGammaSlider();
		updateSaveDumpsCheckBox();
		updateDumpFrequencyField();
		updateSPPTargetField();
		updateSceneNameField();
		enableEmitters.setSelected(renderManager.scene().getEmittersEnabled());
		directLight.setSelected(renderManager.scene().getDirectLight());
		startRenderBtn.setText("RESUME");
		stopRenderBtn.setEnabled(true);
		
		show3DView();
	}

	/**
	 * Update render time status label
	 * @param time Total render time in milliseconds
	 */
	public void setRenderTime(long time) {
		if (renderTimeLbl == null)
			return;
		
		int seconds = (int) ((time / 1000) % 60);
		int minutes = (int) ((time / 60000) % 60);
		int hours = (int) (time / 3600000);
		renderTimeLbl.setText(String.format(
				"Render time: %d hours, %d minutes, %d seconds",
				hours, minutes, seconds));
	}

	/**
	 * Update samples per second status label
	 * @param sps Samples per second
	 */
	public void setSamplesPerSecond(int sps) {
		if (samplesPerSecondLbl == null)
			return;
		
		samplesPerSecondLbl.setText("SPS: " + decimalFormat.format(sps));
	}

	/**
	 * Update SPP status label
	 * @param spp Samples per pixel
	 */
	public void setSPP(int spp) {
		if (sppLbl == null)
			return;
		
		sppLbl.setText("SPP: " + decimalFormat.format(spp));
	}
	
	@Override
	public void setProgress(String task, int done, int start, int target) {
		if (progressBar != null && progressLbl != null && etaLbl != null) {
			progressLbl.setText(String.format("%s: %s of %s",
					task, decimalFormat.format(done), decimalFormat.format(target)));
			progressBar.setMinimum(start);
			progressBar.setMaximum(target);
			progressBar.setValue(Math.min(target, done));
			etaLbl.setText("ETA: N/A");
		}
	}
	
	@Override
	public void setProgress(String task, int done, int start, int target, String eta) {
		if (progressBar != null && progressLbl != null && etaLbl != null) {
			setProgress(task, done, start, target);
			etaLbl.setText("ETA: " + eta);
		}
	}

	/**
	 * Show the 3D view window
	 */
	public void show3DView() {
		view.setCanvasSize(renderManager.scene().canvasWidth(),
				renderManager.scene().canvasHeight());
		view.displayRightOf(this);
	}

	@Override
	public void zoom(int diff) {
		double value = renderManager.scene().camera().getFoV();
		double scale = Camera.MAX_FOV - Camera.MIN_FOV;
		value = value + diff * scale/20;
		value = Math.max(Camera.MIN_FOV, value);
		value = Math.min(Camera.MAX_FOV, value);
		renderManager.scene().camera().setFoV(value);
		updateFovField();
		updateFovSlider();
	}

	/**
	 * @return The render context for this Render Controls dialog
	 */
	public RenderContext getContext() {
		return context;
	}

	@Override
	public void renderStateChanged(boolean pathTrace, boolean paused) {
		if (pathTrace) {
			if (paused) {
				startRenderBtn.setText("RESUME");
			} else {
				startRenderBtn.setText("PAUSE");
			}
			stopRenderBtn.setEnabled(true);
		} else {
			startRenderBtn.setText("START");
			stopRenderBtn.setEnabled(false);
		}
	}

	@Override
	public void chunksLoaded() {
		show3DView();
	}
}