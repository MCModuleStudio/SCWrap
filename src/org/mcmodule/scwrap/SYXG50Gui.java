package org.mcmodule.scwrap;

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.imageio.ImageIO;
import javax.swing.*;

import com.sun.jna.platform.win32.WinDef.HMODULE;

public class SYXG50Gui extends AbstractGui {
	
	private static final long serialVersionUID = 2833432997690576654L;
	
	private final PacketDecoder packetDecoder = new PacketDecoder();
	private final int[] levels = new int[32];
	private final int[] voices = new int[32];
	private int partALevel;
	private int partBLevel;
	private float gain = 1f;
	private SYXG50GuiSetup setupGui;
	private int polyphony;
	private boolean xgMode;
	private boolean showVoices = false;
	private boolean showPartB = false;
	

	public SYXG50Gui(SoundCanvas sc, HMODULE tgModule, SCCoreVersion version) throws IOException {
		super(sc, tgModule, version);
		setTitle("Roland SOUND Canvas VA");
		setLayout(new BorderLayout());
		add(new SYXG50Canvas(), BorderLayout.CENTER);
		pack();
		setLocationByPlatform(true);
		setResizable(false);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
	}

	@Override
	public void process(float[] samples) {
		float gain = Math.max(Math.min(this.gain, 2f), 0f);
		for (int i = 0, len = samples.length; i < len;) {
			samples[i++] *= gain;
			samples[i++] *= gain;
		}
		int polyphony = 0;
		for (int i = 0; i < 16; i++) {
			int block = part2block(i);
			this.levels[i] = level2bar(getBlockLevel(block));
			this.levels[i | 0x10] = level2bar(getBlockLevel(0x10 | block));
			this.voices[i] = getBlockVoiceCount(block);
			this.voices[i | 0x10] = getBlockVoiceCount(0x10 | block);
			polyphony += this.voices[i] + this.voices[i | 0x10];
		}
		this.partALevel = level2bar(getPortLevel(0));
		this.partBLevel = level2bar(getPortLevel(1));
		this.polyphony = polyphony;
		this.xgMode = this.tgModule.getPointer().getShort(SYXG50Gui.this.version.getIsXGModeVariable()) != 0;
		if (this.setupGui != null && !this.setupGui.isDisplayable()) {
			this.setupGui = null;
		}
		if (this.setupGui != null) {
			this.setupGui.update();
		}
		int result = readEventQueue(2);
		if (result != 0) {
			if ((result & 0xFF) == 0) {
				String error = null;
				switch (result) {
				case 0x81040000:
					error = "Checksum Error";
					break;
				case 0x81020000:
					error = "MIDI Buff. Full";
					break;
				case 0x81010000:
					error = "MIDI Off Line";
					break;
				case 0x81080000:
					error = "No Instrument";
					break;
				case 0x81090000:
					error = "No Drum Set";
					break;
				default:
					break;
				}
				if (error != null) {
					System.out.println(error);
				}
			} else {
				this.packetDecoder.decodeMessage(result);
//				byte[] decodedMessage = this.packetDecoder.decodeMessage(result);
//				if (decodedMessage != null) {
//					System.out.println("TX: " + SoundCanvas.toHex(decodedMessage, decodedMessage.length));
//				}
			}
		}
	}
	
	public boolean inArea(MouseEvent e, int x1, int y1, int x2, int y2) {
		int x = e.getX();
		int y = e.getY();
		return x >= x1 && y >= y1 && x <= x2 && y <= y2;
	}

	public void openSetup() {
		this.setupGui = new SYXG50GuiSetup(this);
		this.setupGui.setVisible(true);
	}
	
	class SYXG50GuiSetup extends JDialog implements ActionListener {
		
		private static final long serialVersionUID = -6719836245754146475L;

		private final SYXG50Gui gui;
		private final JSlider gainSlider;
		private final JLabel gainLabel;
		private final JRadioButton[] mapButton = new JRadioButton[4], modeButton = new JRadioButton[4];

		private final JRadioButton levelButton, voiceButton, partAButton, partBButton;

		public SYXG50GuiSetup(SYXG50Gui syxg50Gui) {
			super(syxg50Gui, "Setup", true);
			this.gui = syxg50Gui;
			setLayout(new BorderLayout());
			JTabbedPane tp = new JTabbedPane();
			add(tp, BorderLayout.CENTER);
			Box box = Box.createHorizontalBox();
			box.add(Box.createHorizontalGlue());
			JButton preview = new JButton("Preview");
			JButton instMap = new JButton("Inst map");
			JButton close = new JButton("Close");
			box.add(preview);
			box.add(Box.createHorizontalStrut(2));
			box.add(instMap);
			box.add(Box.createHorizontalStrut(2));
			box.add(close);
			add(box, BorderLayout.SOUTH);
			preview.addMouseListener(new ButtonAdaptor(Button.PREVIEW_PRESSED, Button.PREVIEW_RELEASED));
			instMap.addMouseListener(new ButtonAdaptor(Button.INSTMAP_PRESSED, Button.INSTMAP_RELEASED));
			close.addActionListener(e -> dispose());
			JPanel settingPanel = new JPanel();
			settingPanel.setPreferredSize(new Dimension(480, 320));
			JPanel outputLevelPanel = new JPanel();
			outputLevelPanel.setBorder(BorderFactory.createTitledBorder("Output Level"));
			outputLevelPanel.setPreferredSize(new Dimension(450, 60));
			outputLevelPanel.setLayout(null);
			this.gainSlider = new JSlider(-121, 30, 0);
			outputLevelPanel.add(this.gainSlider);
			this.gainSlider.setBounds(10, 25, 360, 20);
			this.gainLabel = new JLabel("  +0.0 dB");
			this.gainLabel.setBorder(BorderFactory.createLoweredBevelBorder());
			outputLevelPanel.add(this.gainLabel);
			this.gainLabel.setBounds(370, 20, 60, 30);
			settingPanel.add(outputLevelPanel);
			JPanel mapPanel = new JPanel();
			mapPanel.setBorder(BorderFactory.createTitledBorder("Map"));
			mapPanel.setPreferredSize(new Dimension(450, 60));
			mapPanel.setLayout(null);
			ButtonGroup mapGroup = new ButtonGroup();
			Map[] maps = Map.values();
			for (int i = 0; i < 4; i++) {
				JRadioButton mapButton = new JRadioButton(maps[i + 1].getName());
				mapButton.setBounds(35 + i * 100, 23, 100, 20);
				mapPanel.add(mapButton);
				mapGroup.add(mapButton);
				this.mapButton[i] = mapButton;
			}
			settingPanel.add(mapPanel);
			JPanel modePanel = new JPanel();
			modePanel.setBorder(BorderFactory.createTitledBorder("Mode"));
			modePanel.setPreferredSize(new Dimension(450, 60));
			modePanel.setLayout(null);
			ButtonGroup modeGroup = new ButtonGroup();
			Mode[] modes = Mode.values();
			for (int i = 0; i < 4; i++) {
				JRadioButton modeButton = new JRadioButton(modes[i].name());
				modeButton.setBounds(35 + i * 100, 23, 100, 20);
				modePanel.add(modeButton);
				modeGroup.add(modeButton);
				this.modeButton[i] = modeButton;
			}
			settingPanel.add(modePanel);
			JPanel barDisplayPanel = new JPanel();
			barDisplayPanel.setBorder(BorderFactory.createTitledBorder("Bar display"));
			barDisplayPanel.setPreferredSize(new Dimension(450, 60));
			barDisplayPanel.setLayout(null);
			ButtonGroup barGroup = new ButtonGroup();
			ButtonGroup partGroup = new ButtonGroup();
			JRadioButton levelButton = new JRadioButton("Level");
			levelButton.setBounds(35 + 0 * 100, 23, 100, 20);
			JRadioButton voiceButton = new JRadioButton("Voice");
			voiceButton.setBounds(35 + 1 * 100, 23, 100, 20);
			JRadioButton partAButton = new JRadioButton("Part A");
			partAButton.setBounds(35 + 2 * 100, 23, 100, 20);
			JRadioButton partBButton = new JRadioButton("Part B");
			partBButton.setBounds(35 + 3 * 100, 23, 100, 20);
			barGroup.add(levelButton);
			barGroup.add(voiceButton);
			partGroup.add(partAButton);
			partGroup.add(partBButton);;
			barDisplayPanel.add(levelButton);
			barDisplayPanel.add(voiceButton);
			barDisplayPanel.add(partAButton);
			barDisplayPanel.add(partBButton);
			settingPanel.add(barDisplayPanel);
			tp.add("Settings", settingPanel);
			pack();
			setLocationRelativeTo(syxg50Gui);
			setResizable(false);
			updateSetting();
			levelButton.setSelected(true);
			partAButton.setSelected(true);
			this.levelButton = levelButton;
			this.voiceButton = voiceButton;
			this.partAButton = partAButton;
			this.partBButton = partBButton;
			levelButton.addActionListener(this);
			voiceButton.addActionListener(this);
			partAButton.addActionListener(this);
			partBButton.addActionListener(this);
			this.gainSlider.addChangeListener(l -> {
				int value = this.gainSlider.getValue();
				if (value == -121) {
					this.gui.gain = 0f;
					this.gainLabel.setText("       -∞ dB");
				} else {
					double dB = value * 0.2d;
					this.gui.gain = (float) Math.pow(10d, dB / 20d);
					this.gainLabel.setText(String.format("%8.1f dB", dB));
				}
			});
			this.gainSlider.addMouseWheelListener(l -> {
				int scrollType = l.getScrollType();
				if (scrollType == MouseWheelEvent.WHEEL_UNIT_SCROLL) {
					this.gainSlider.setValue(this.gainSlider.getValue() + l.getUnitsToScroll());
				} else if (scrollType == MouseWheelEvent.WHEEL_BLOCK_SCROLL) {
					this.gainSlider.setValue(this.gainSlider.getValue() + Integer.signum(l.getWheelRotation()));
				}
			});
			for (int i = 0; i < 4; i++) {
				this.mapButton[i].addActionListener(this);
				this.modeButton[i].addActionListener(this);
			}
		}
		
		public void update() {
			boolean xgMode = SYXG50Gui.this.xgMode;
			Map currentMap = getMap();
			this.mapButton[currentMap.ordinal() - 1].setSelected(true);
			// FIXME: Support read more mode
			Mode currentMode = xgMode ? Mode.XG : Mode.GS;
			this.modeButton[currentMode.ordinal()].setSelected(true);
			boolean notXGMode = !xgMode;
			for (int i = 0; i < 4; i++) {
				this.mapButton[i].setEnabled(notXGMode);
			}
		}

		private void updateSetting() {
			float gain = this.gui.gain;
			if (gain <= 0f) {
				this.gainSlider.setValue(-121);
				this.gainLabel.setText("       -∞ dB");
			} else {
				double dB = 20d * Math.log10(gain);
				this.gainSlider.setValue((int) (dB / 0.2d));
				this.gainLabel.setText(String.format("%8.1f dB", dB));
			}
			update();
		}
		
		@Override
		public void actionPerformed(ActionEvent e) {
			Object source = e.getSource();
			for (int i = 0; i < 4; i++) {
				if (source == this.mapButton[i]) {
					Map[] maps = Map.values();
					Map current = getMap();
					Map target = maps[i + 1];
					while (current != target) {
						sendButtonEvent(Button.INSTMAP_PRESSED);
						current = maps[current.ordinal() - 1];
						if (current == Map.SELECTED)
							current = Map.SC8820;
					}
					return;
				}
				if (source == this.modeButton[i]) {
					Mode[] modes = Mode.values();
					SYXG50Gui.this.sc.postMidi(0, modes[i].getSysex());
					return;
				}
			}
			if (source == this.levelButton) {
				SYXG50Gui.this.showVoices = false;
			}
			if (source == this.voiceButton) {
				SYXG50Gui.this.showVoices = true;
			}
			if (source == this.partAButton) {
				SYXG50Gui.this.showPartB = false;
			}
			if (source == this.partBButton) {
				SYXG50Gui.this.showPartB = true;
			}
		}

		class ButtonAdaptor implements MouseListener {
			
			private final Button pressed;
			private final Button released;

			ButtonAdaptor(Button pressed, Button released) {
				this.pressed = pressed;
				this.released = released;
			}

			@Override
			public void mouseClicked(MouseEvent e) {}

			@Override
			public void mousePressed(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) 
					sendButtonEvent(this.pressed);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				if (e.getButton() == MouseEvent.BUTTON1) 
					sendButtonEvent(this.released);
			}

			@Override
			public void mouseEntered(MouseEvent e) {}

			@Override
			public void mouseExited(MouseEvent e) {}
		}
	}

	class SYXG50Canvas extends Canvas implements Runnable, MouseListener, MouseMotionListener {
		
		private static final long serialVersionUID = -6539522276208810420L;
		
		private final BufferedImage background;
		
		private Thread renderThread;

		private boolean setupPressed;
		
		public SYXG50Canvas() throws IOException {
			setSize(400, 90);
			this.background = ImageIO.read(SYXG50Canvas.class.getResourceAsStream("/background.png"));
			this.addMouseListener(this);
			this.addMouseMotionListener(this);
		}
		
		@Override
		public void paint(Graphics g) {
			Graphics2D g2d = (Graphics2D) g;
			g2d.drawImage(this.background, 0, 0, 400, 90, 0, 0, 400, 90, this);
			if (this.setupPressed) {
				g2d.drawImage(this.background, 309, 64, 389, 80, 309, 122, 389, 138, this);
			}
			g2d.drawImage(this.background, 11, 33, 299, 80, 11, 90, 299, 137, this);
			int[] bar = SYXG50Gui.this.showVoices ? SYXG50Gui.this.voices : SYXG50Gui.this.levels;
			int offset = SYXG50Gui.this.showPartB ? 16 : 0;
			for (int i = 0; i < 16; i++) {
				drawBar(g2d, 19 + 11 * i, 37, bar[i + offset]);
			}
			drawBar(g2d, 205, 37, SYXG50Gui.this.partALevel);
			drawBar(g2d, 216, 37, SYXG50Gui.this.partBLevel);
			drawString(g2d, 256, 49, String.format("%3d", SYXG50Gui.this.polyphony));
			boolean xg = SYXG50Gui.this.xgMode;
			if (xg) {
				g2d.drawImage(this.background, 308, 32, 349, 60, 308, 90, 349, 118, this);
			} else {
				g2d.drawImage(this.background, 351, 32, 388, 61, 351, 90, 388, 119, this);
			}
		}
		
		private void drawString(Graphics2D g2d, int x, int y, String str) {
			for (int i = 0, len = str.length(); i < len; i++) {
				int idx = "0123456789 ".indexOf(str.charAt(i));
				if (idx < 0)
					idx = 0;
				g2d.drawImage(this.background, x, y, x + 10, y + 13, 189 + 10 * idx, 137, 189 + 10 * idx + 10, 150, this);
				x += 10;
			}
		}

		private void drawBar(Graphics2D g2d, int x, int y, int level) {
			int lev = Math.max(Math.min((level + 1) >> 1, 8), 0);
			g2d.drawImage(this.background, x, y, x + 11, y + 28, 19 + 11 * lev, 137, 19 + 11 * lev + 11, 165, this);
		}

		@Override
		public void addNotify() {
			super.addNotify();
			this.createBufferStrategy(2);
			this.renderThread = new Thread(this);
			this.renderThread.start();
		}
		
		@Override
		public void removeNotify() {
			this.renderThread.interrupt();
			try {
				this.renderThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			super.removeNotify();
		}
		
		@Override
		public void run() {
			while (!Thread.currentThread().isInterrupted()) {
				BufferStrategy bufferStrategy = this.getBufferStrategy();
				paint(bufferStrategy.getDrawGraphics());
				bufferStrategy.show();
				try {
					Thread.sleep(1000L / 30L);
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		@Override
		public void mouseClicked(MouseEvent e) {}

		@Override
		public void mousePressed(MouseEvent e) {
			if (e.getButton()  == MouseEvent.BUTTON1) {
				this.setupPressed = inArea(e, 308, 63, 390, 81);
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (e.getButton()  == MouseEvent.BUTTON1) {
				this.setupPressed = false;
				if (inArea(e, 308, 63, 390, 81)) {
					openSetup();
				}
			}
		}

		@Override
		public void mouseEntered(MouseEvent e) {}

		@Override
		public void mouseExited(MouseEvent e) {}

		@Override
		public void mouseDragged(MouseEvent e) {
			if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) != 0) {
				this.setupPressed = inArea(e, 308, 63, 390, 81);
			}
		}

		@Override
		public void mouseMoved(MouseEvent e) {}
	}
	
	static enum Mode {
		XG ("\360\103\020\114\000\000\176\000\367"),
		GS ("\360\101\020\102\022\000\000\177\000\001\367"),
		GM1("\360\176\177\011\001\367"),
		GM2("\360\176\177\011\003\367");

		private final byte[] sysex;

		Mode(String sysex) {
			this.sysex = sysex.getBytes(StandardCharsets.ISO_8859_1);
		}

		public byte[] getSysex() {
			return this.sysex;
		}
	}
}
