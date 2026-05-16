package org.mcmodule.scwrap.gui;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferStrategy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.DefaultButtonModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

import org.mcmodule.scwrap.SoundCanvas;
import org.mcmodule.scwrap.util.PacketDecoder;
import org.mcmodule.scwrap.util.SCCoreVersion;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HMODULE;

public class SC88ProGui extends AbstractGui {
	
	private static final byte[][] DUMP_INSTRUMENTS_SYSEX = {
			"\360\101\020\102\021\014\000\001\000\000\000\163\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\002\000\000\000\162\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\003\000\000\001\160\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\003\000\000\002\157\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\003\000\000\003\156\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\003\000\000\004\155\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\021\014\000\004\000\000\000\160\367".getBytes(StandardCharsets.ISO_8859_1),
//			"\360\101\020\102\021\014\000\005\000\000\000\157\367".getBytes(StandardCharsets.ISO_8859_1)
	};
	
	private final LinkedHashMap<Integer, String> instrumentList = new LinkedHashMap<>();
	private final LinkedHashMap<Integer, String> drumSetList = new LinkedHashMap<>();
	private final LinkedHashMap<Integer, String> drumInstrumentList = new LinkedHashMap<>();
	private final LinkedHashMap<Integer, String> efxList = new LinkedHashMap<>();
//	private final LinkedHashMap<Integer, String> presetPatchList = new LinkedHashMap<>();

	private static final long serialVersionUID = 7683827167361170313L;
//	private final SoftSynth softSynth;
	private final SCCanvas canvas;
	private final PacketDecoder[] packetDecoder = new PacketDecoder[6];
	private TestModeDisplay testModeDisplay;
	private TempControlPanel controlPanel;
	private int dumpInstruments = -1;
	
	public SC88ProGui(SoundCanvas sc, HMODULE tgModule, SCCoreVersion version) {
		super(sc, tgModule, version);
//		this.softSynth = softSynth;
		this.canvas = new SCCanvas();
		add(this.canvas);
		pack();
		setTitle("SCWrap");
		setResizable(false);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLocationByPlatform(true);
		PacketDecoder[] packetDecoder = this.packetDecoder;
		for (int i = 0, len = packetDecoder.length; i < len; i++) {
			packetDecoder[i] = new PacketDecoder();
		}
		// FIXME
		if (version != SCCoreVersion.Y2015_REV1_64BIT) {
			this.dumpInstruments = 0;
		}
	}
	
	@Override
	public void process(float[] samples) {
		if (this.dumpInstruments >= 0) {
			SoundCanvas sc = this.sc;
			this.canvas.showSystemMessage("Please wait.....");
			// Dump instruments, drums, etc...
//			sc.postMidi(0, "\360\101\020\102\021\014\000\001\000\000\000\163\367\360\101\020\102\021\014\000\002\000\000\000\162\367\360\101\020\102\021\014\000\003\000\000\001\160\367\360\101\020\102\021\014\000\003\000\000\002\157\367\360\101\020\102\021\014\000\003\000\000\003\156\367\360\101\020\102\021\014\000\003\000\000\004\155\367\360\101\020\102\021\014\000\004\000\000\000\160\367\360\101\020\102\021\014\000\005\000\000\000\157\367".getBytes(StandardCharsets.ISO_8859_1));
			sc.postMidi(0, DUMP_INSTRUMENTS_SYSEX[this.dumpInstruments++]);
			if (this.dumpInstruments >= DUMP_INSTRUMENTS_SYSEX.length)
				this.dumpInstruments  = -1;
		}
		int result;
		do {
			result = readEventQueue(2);
			if ((result & 0xFF) == 0) {
				String error = null;
				switch (result) {
				case 0x81000000:
					error = "   Hard Error   ";
					break;
				case 0x81010000:
					error = " MIDI Off Line  ";
					break;
				case 0x81020000:
					error = "MIDI Buff. Full.";
					break;
				case 0x81040000:
					error = "Check Sum Error ";
					break;
				case 0x81080000:
					error = " No INSTRUMENT  ";
					break;
				case 0x81090000:
					error = "  No DRUM SET   ";
					break;
				default:
					break;
				}
				if (error != null) {
					this.canvas.showSystemMessage(error);
				}
			} else {
				byte[] decodedMessage = this.packetDecoder[2].decodeMessage(result);
				if (decodedMessage != null) {
					if ((decodedMessage[0] & 0xFF) == 0xF0 && SoundCanvas.checksum(decodedMessage, decodedMessage.length) == 0) {
						if ((decodedMessage[0] & 0xFF) == 0xF0 && decodedMessage[1] == 0x41 && decodedMessage[2] == getDeviceID() && decodedMessage[3] == 0x42 && decodedMessage[4] == 0x12 && decodedMessage[5] == 0x0C) {
							int addrL = decodedMessage[7];
							LinkedHashMap<Integer, String> list = null;
							int length = 12;
							switch (addrL) {
							case 1:
								list = this.instrumentList;
								break;
							case 2:
								list = this.drumSetList;
								break;
							case 3:
								list = this.drumInstrumentList;
								break;
							case 4:
								list = this.efxList;
								length = 16;
								break;
							default:
								break;
							}
							if (list != null) {
								byte[] buf = new byte[length];
								ByteBuffer byteBuf = ByteBuffer.wrap(decodedMessage, 8, decodedMessage.length - 10).order(ByteOrder.BIG_ENDIAN);
								while (byteBuf.hasRemaining()) {
									int index = byteBuf.getInt();
									byteBuf.get(buf);
									String name = new String(buf, StandardCharsets.ISO_8859_1);
//									System.out.printf("%08x. %s\n", index, name);
									list.put(index, name);
									
								}
							}
						}
					}
//					this.canvas.showSystemMessage("Transmitting... ");
				}
				if (decodedMessage != null) {
//					System.out.println("TX: " + SoundCanvas.toHex(decodedMessage, decodedMessage.length));
				}
			}
		} while (result != 0);
		do {
			result = readEventQueue(4);
			if ((result & 0xFF) != 0) {
				byte[] decodedMessage = this.packetDecoder[4].decodeMessage(result);
				if (decodedMessage != null) {
//					System.out.println("RX: " + SoundCanvas.toHex(decodedMessage, decodedMessage.length));
					this.canvas.handleMidiMessage(decodedMessage, (result & 0xF0) >> 4);
					if ((decodedMessage[0] & 0xFF) == 0xF0 && decodedMessage[1] == 0x41 && decodedMessage[2] == getDeviceID() && decodedMessage[3] == 0x45 && decodedMessage[4] == 0x12 && decodedMessage[5] == 0x20 && SoundCanvas.checksum(decodedMessage, decodedMessage.length) == 0) {
						if (this.testModeDisplay == null || !this.testModeDisplay.isDisplayable()) {
							this.testModeDisplay  = new TestModeDisplay(this.testModeDisplay);
							this.testModeDisplay.setVisible(true);
							this.testModeDisplay.toFront();
						}
						this.testModeDisplay.parse(decodedMessage);
					}
				}
			}
		} while (result != 0);
		this.onUpdate();
	}
	
	@Override@SuppressWarnings("deprecation")
	public void show() {
		super.show();
		if (this.controlPanel == null) {
			TempControlPanel controlPanel = this.controlPanel = new TempControlPanel();
			SwingUtilities.invokeLater(() -> {
				controlPanel.setLocation(getX() + getContentPane().getWidth(), getY());
				controlPanel.setVisible(true);
			});
		}
	}
	
	@Override@SuppressWarnings("deprecation")
	public void hide() {
		super.hide();
		if (this.controlPanel != null) {
			this.controlPanel.dispose();
			this.controlPanel = null;
		}
		if (this.testModeDisplay != null) {
			this.testModeDisplay.dispose();
		}
	}
	
	public void onUpdate() {
		this.canvas.doUpdate();
	}
	
	public void setLcdColor(Color color) {
		this.canvas.setLCDColor(color);
	}

	public void setPixelAt(int x, int y, boolean on) {
		this.canvas.setPixelAt(x, y, on);
	}

	public void reset() {
		this.canvas.reset();
	}

	public void showDisplayLetter(String str) {
		this.canvas.showDisplayLetter(str);
	}
	
	public void showSystemMessage(String str) {
		this.canvas.showSystemMessage(str);
	}

	public class SCCanvas extends Canvas implements Runnable {

		// WHY???? ORACLE
		private        final int[] SC_8850_LEVEL2BAR = {-1, 0x08, 0x0c, 0x10, 0x12, 0x14, 0x16, 0x18, 0x1a, 0x1c, 0x1e, 0x20, 0x22, 0x24, 0x26, 0x28, 0x29, 0x2a, 0x2b, 0x2c, 0x2d, 0x2e, 0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x3f, 0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49, 0x4a, 0x4b, 0x4c, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59, 0x5a, 0x5b, 0x5c, 0x5d, 0x5e, 0x5f, 0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69, 0x6a, 0x6b, 0x6c, 0x6d, 0x6e, 0x6f, 0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x78, 0x78, 0x78, 0x78, 0x79, 0x79, 0x79, 0x79, 0x79, 0x7a, 0x7a, 0x7a, 0x7a, 0x7a, 0x7b, 0x7b, 0x7b, 0x7b, 0x7c, 0x7c, 0x7c, 0x7c, 0x7c, 0x7d, 0x7d, 0x7d, 0x7d, 0x7d, 0x7e, 0x7e, 0x7e, 0x7e, 0x7f};
		private        final int[] SC_88PRO_LEVEL2BAR = {-1, 0x10, 0x10, 0x10, 0x10, 0x10, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x18, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x20, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x28, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x38, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x40, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x48, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x50, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x58, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x60, 0x68, 0x68};
		private static final long serialVersionUID = 5413712560816394611L;
		private final long[] screenData = new long[] {0x0000000000000000L, 0x0000000000000000L, 0x0000000000000000L, 0x000000000000FFFFL};
		private final CharacterRenderer characterRenderer = new CharacterRenderer();
		private final int[] bars                    = new int [PARTS],
							barTimers               = new int [PARTS],
							peakHolder              = new int [PARTS];
		private final long[] peakHolderTimer        = new long[PARTS];
		private final long[][] displayDotData       = new long[10][4];
		private final char[] currentDisplayedString = new char[16];
		private final byte[] bitmaps = new byte[9280];
		private final ReentrantLock lock = new ReentrantLock();
		
		private int currentDisplayedScreen = -1;
		
		private Color backgroundColor, offColor, onColor;
		
		private        final Font font = Font.decode("Arial").deriveFont(28f).deriveFont(getScaledTransform(1.2d));
		
		private static final int LCD_WIDTH     = 1280,
								 LCD_HEIGHT    =  480,
								 LCD_PADDING_X = 60,
								 LCD_PADDING_Y = 40,
								 LCD_GAP = 40,

								 FONT_WIDTH  = 50,
								 FONT_HEIGHT = 70,
								 FONT_GAP_X  = 10,

								 BARS_WIDTH  = FONT_WIDTH * 12 + FONT_GAP_X * 11,
								 BARS_HEIGHT = FONT_HEIGHT * 3 + LCD_GAP * 2,
								 BARS_BASE_X = LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2) + LCD_GAP + (FONT_WIDTH + FONT_GAP_X) * 4,
								 BARS_BASE_Y = LCD_PADDING_Y + FONT_HEIGHT + LCD_GAP,
								 SCROLL_SPEED = 200,
								 PARTS       = 32;
		
		private String part, instrument, level, pan, reverb, chorus, kshift, midich;
		private int displayTime = 6;
		private long currentDisplayTimer = Long.MAX_VALUE, currentDisplayLetterTimer = Long.MAX_VALUE, currentSystemMessageTimer = Long.MAX_VALUE;
		private String displayLetter, systemMessage;
		private int displayLetterOffset = 0;
		private Thread renderThread;
		
		private int bitmapIndex = -1, bitmapEnd = 0, bitmapDelay;
		private long bitmapTimer = Long.MAX_VALUE;
		private boolean inspectAll;
		private int selectedPart = 0;
		
		public SCCanvas() {
			super();
			setPreferredSize(new Dimension(1280, 480));
			setLCDColor(Color.getHSBColor(30f / 360f, 1f, 1f));
			this.part = "";
			this.midich = "";
			this.instrument = "";
			this.level = "";
			this.pan = "";
			this.kshift = "";
			this.reverb = "";
			this.chorus = "";
			for (int i = 0; i < PARTS; i++) {
				this.peakHolderTimer[i] = System.currentTimeMillis();
			}
			setCurrentDisplayedString(this.instrument);
			reset();
			try (InputStream in = SCCanvas.class.getResourceAsStream("/SC88ProBitmap.bin")) {
				in.read(this.bitmaps);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		@Override
		public void addNotify() {
			super.addNotify();
			this.renderThread = new Thread(this);
			this.renderThread.setName("Gui Thread");
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
			this.bitmapIndex = 9;
			this.bitmapEnd = 63;
//			this.bitmapIndex = 77;
//			this.bitmapEnd = 144;
			this.bitmapDelay = 50;
			this.bitmapTimer = System.currentTimeMillis();
			this.createBufferStrategy(2);
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

		public void setCurrentDisplayedString(String str) {
			char[] charArray = str.toCharArray();
			int len = Math.min(charArray.length, 16);
			Arrays.fill(this.currentDisplayedString, ' ');
			System.arraycopy(charArray, 0, this.currentDisplayedString, (16 - len) / 2 + (len & 1), len);
		}

		public void setLCDColor(Color color) {
//			this.backgroundColor = color;
//			this.offColor = new Color((int) (color.getRed() * 0.875), (int) (color.getGreen() * 0.875), (int) (color.getBlue() * 0.875));
//			this.onColor  = new Color((int) (color.getRed() * 0.25 ), (int) (color.getGreen() * 0.25 ), (int) (color.getBlue() * 0.25 ));
//			this.characterRenderer.setColor(this.backgroundColor, this.offColor, this.onColor);
			// Emulates real Sound Canvas unit
			int contrast = 12;
			float con = 1f - (float) (Math.pow((contrast - 1) / 15d, 2.2d) * 0.25 + 0.015625);
			this.backgroundColor = color;
			color = this.offColor = new Color((int) (color.getRed() * con), (int) (color.getGreen() * con), (int) (color.getBlue() * con));
			con = 1f - ((contrast + 1) / 32f + 0.25f);
			this.onColor = new Color((int) (color.getRed() * con), (int) (color.getGreen() * con), (int) (color.getBlue() * con));
			this.characterRenderer.setColor(this.backgroundColor, this.offColor, this.onColor);
		}

		public void paint(Graphics g) {
			doPaint((Graphics2D) g);
		}
		
		private void doPaint(Graphics2D g) {
			g.scale(getWidth() / (double) LCD_WIDTH, getHeight() / (double) LCD_HEIGHT);
			this.lock.lock();
			try {
				renderCanvas(g);
			} finally {
				this.lock.unlock();
			}
			g.dispose();
		}

		protected void renderCanvas(Graphics2D g) {
			
			final long currentTime = System.currentTimeMillis();
			
			int[] bars = this.bars;
			int[] peakHolder = this.peakHolder;
			long[] peakHolderTimer = this.peakHolderTimer;
			
			for (int i = 0; i < PARTS; i++) {
				int bar = bars[i] >> 3;
				if (bar >= peakHolder[i] - 1) {
					peakHolder[i] = bar + 1;
					peakHolderTimer[i] = currentTime + 300L;
				}
				while(currentTime - peakHolderTimer[i] >= 200L) {
					if (bar < peakHolder[i] - 1) {
						peakHolder[i]--;
					}
					this.peakHolderTimer[i] += 200L;
				}
			}
			
			Arrays.fill(this.screenData, 0L); // clear screen
			final boolean doubleMode = this.inspectAll;
			if (doubleMode) {
				for(int i = 0; i < 32; i++) {
					if (peakHolder[i] > 0 && peakHolder[i] <= 16)
						setPixelAt(i & 0xF, 16 - ((peakHolder[i] + 1) >> 1) - ((i & 0x10) >> 1), true);
					int bar = bars[i] >> 3;
					for (int j = 1; j <= bar; j++) {
						setPixelAt(i & 0xF, 16 - (j + 1) / 2 - ((i & 0x10) >> 1), true);
					}
					if ((blockBase.getShort(1160 * part2block(i) + 0x3d6) & 0x200) != 0)
						setPixelAt(i & 0xF, 15 - ((i & 0x10) >> 1), true);
				}
			} else {
				int off = selectedPart & ~0xF;
				for (int i = 0; i < 16; i++) {
					if (peakHolder[i + off] > 0 && peakHolder[i + off] <= 16)
						setPixelAt(i, 16 - peakHolder[i + off], true);
					int bar = bars[i + off] >> 3;
					for (int j = 1; j <= bar; j++) {
						setPixelAt(i, 16 - j, true);
					}
					if ((blockBase.getShort(1160 * part2block(i + off) + 0x3d6) & 0x200) != 0)
						setPixelAt(i, 15, true);
				}
			}
			
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			
			g.setColor(this.backgroundColor);
			g.fillRect(0, 0, LCD_WIDTH, LCD_HEIGHT);
			drawLCDStringR(LCD_PADDING_X, LCD_PADDING_Y, this.part, 3, g); // Part
			drawLCDString(LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y, this.currentDisplayedString, g); // Instrument
			drawLCDStringR(LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 1, this.level, 3, g); // Level
			drawLCDStringR(LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 1, this.pan, 3, g); // Pan
			drawLCDStringR(LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 2, this.reverb, 3, g); // Reverb
			drawLCDStringR(LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 2, this.chorus, 3, g); // Chorus
			drawLCDStringR(LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 3, this.kshift, 3, g); // K shift
			drawLCDStringR(LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 3, this.midich, 3, g); // MIDI ch
			
			// Display screen
			final int stepX = BARS_WIDTH  / 16;
			final int stepY = BARS_HEIGHT / 16;
			final int gapX  = stepX / 20;
			final int gapY  = stepX / 20;
			
			long[] screenData;
			int currentDisplayedScreen = this.currentDisplayedScreen;
			
			if (currentDisplayedScreen >= 0  &&  currentDisplayedScreen < 10) {
				screenData = this.displayDotData[currentDisplayedScreen];
			} else {
				screenData = this.screenData;
				if (this.bitmapIndex >= 0) {
					byte[] data = new byte[0x40];
					System.arraycopy(this.bitmaps, this.bitmapIndex * 0x40, data, 0, 0x40);
					Arrays.fill(screenData, 0x0000000000000000L);
					for (int i = 0; i < 16; i++) {
						int rowData = (data[i] << 11) | (data[i + 16]) << 6 | (data[i + 32] << 1) | (data[i + 48] >> 4);
						for (int j = 0; j < 16; j++) {
							setPixelAt(screenData, j, i, (rowData & (1 << (15 - j))) != 0);
						}
					}
				}
			}
			
			for(int y = 0; y < 16; y++) {
				for(int x = 0; x < 16; x++) {
					long l = screenData[y >> 2];
					boolean on = (l & (1L << (((3 - (y & 3L)) << 4L) | x))) != 0L;
					g.setColor(on ? this.onColor : this.offColor);
					g.fillRect(BARS_BASE_X + stepX * x, BARS_BASE_Y + stepY * y, stepX - gapX, stepY - gapY);
				}
			}
			
			// Labels
			g.setFont(this.font);
			g.setColor(Color.black);
			AffineTransform transform = g.getTransform();
			FontMetrics fontMetrics = g.getFontMetrics();
			g.translate(0, fontMetrics.getAscent() * 0.2d);
			g.drawString("LEVEL", LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 1 - 12);
			g.drawString("PAN", LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 1 - 12);
			g.drawString("REVERB", LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 2 - 12);
			g.drawString("CHORUS", LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 2 - 12);
			g.drawString("K SHIFT", LCD_PADDING_X, LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 3 - 12);
			g.drawString("MIDI CH", LCD_PADDING_X + (FONT_WIDTH * 3 + FONT_GAP_X * 2 + LCD_GAP), LCD_PADDING_Y + (FONT_HEIGHT + LCD_GAP) * 3 - 12);
			for(int x = 0; x < 16; x++) {
				String str = String.valueOf(x + 1);
				g.drawString(str, BARS_BASE_X + stepX * x + (stepX - fontMetrics.stringWidth(str)) / 2, BARS_BASE_Y + BARS_HEIGHT + 28f * 0.75f);
			}
			g.setTransform(transform);
		}

		protected void drawLCDString(int x, int y, char[] charArray, Graphics2D g) {
			CharacterRenderer characterRenderer = this.characterRenderer;
			int length = charArray.length;
			for(int i = 0; i < length; i++) {
				characterRenderer.drawCharacter(x, y, charArray[i], g, this);
				x += FONT_WIDTH + FONT_GAP_X;
			}
		}

		protected void drawLCDStringR(int x, int y, String str, int length, Graphics2D g) {
			CharacterRenderer characterRenderer = this.characterRenderer;
			if (str.length() < length) {
				int pad = length - str.length();
				for (int i = 0; i < pad; i++) {
					characterRenderer.drawCharacter(x, y, ' ', g, this);
					x += FONT_WIDTH + FONT_GAP_X;
				}
				length = str.length();
			}
			for (int i = 0; i < length; i++) {
				characterRenderer.drawCharacter(x, y, str.charAt(i), g, this);
				x += FONT_WIDTH + FONT_GAP_X;
			}
		}
		
		protected void drawLCDStringL(int x, int y, String str, int length, Graphics2D g) {
			CharacterRenderer characterRenderer = this.characterRenderer;
			int i = 0;
			for (int len = Math.min(length, str.length()); i < len; i++) {
				characterRenderer.drawCharacter(x, y, str.charAt(i), g, this);
				x += FONT_WIDTH + FONT_GAP_X;
			}
			for (; i < length; i++) {
				characterRenderer.drawCharacter(x, y, ' ', g, this);
				x += FONT_WIDTH + FONT_GAP_X;
			}
		}

		public void doUpdate() {
			if (this.lock.tryLock()) {
				try {
					onUpdate();
				} finally {
					this.lock.unlock();
				}
			}
		}

		protected void onUpdate() {
			Pointer blockBase = SC88ProGui.this.blockBase;
			Pointer patchBase = SC88ProGui.this.patchBase;
			Pointer setupBase = SC88ProGui.this.setupBase;
			int selectedPart = this.selectedPart = Math.max(Math.min(this.selectedPart, PARTS - 1), 0);
			if (this.inspectAll) {
				this.part = "ALL";
				byte[] instruments = patchBase.getByteArray(0, 16);
				this.instrument = new String(instruments, StandardCharsets.ISO_8859_1);
				this.level = String.valueOf(setupBase.getByte(2) & 0xFF);
				int pan = setupBase.getByte(6) - 64;
				if (pan == 0)
					this.pan = "0";
				else
					this.pan = String.format("%c%2d", pan < 0 ? 'L' : 'R', Math.abs(pan));
				this.reverb = String.valueOf(patchBase.getByte(0x29) & 0xFF);
				this.chorus = String.valueOf(patchBase.getByte(0x30) & 0xFF);
				int kshift = setupBase.getByte(5) - 64;
				if (kshift == 0)
					this.kshift = "± 0";
				else
					this.kshift = String.format("%c%2d", pan < 0 ? '-' : '+', Math.abs(kshift));
				this.midich = "A--";
				for (int i = 0; i < 16; i++)
					this.currentDisplayedString[i] = (char) (instruments[i] & 0xFF);
			} else {
				char[] currentDisplayedString = this.currentDisplayedString;
				this.part = String.format("%c%02d", 'A' + (selectedPart >> 4), (selectedPart & 0xF) + 1);
				Pointer block = new Pointer(Pointer.nativeValue(blockBase) + 1160 * part2block(selectedPart));
				Pointer rhythm = block.getPointer(0x18);
				int rxBankFlags = block.getByte(0x3ec) & 0xFF;
				int toneNo = block.getShort(0x232) & 0xFFFF;
				int bankNo = block.getByte(0x3d4) & 0xFF;
				int progNo = block.getByte(0x3d5) & 0xFF;
				int mapNo = block.getByte(0x44d) & 0xFF;
				if (mapNo == 0)
					mapNo  = block.getByte(0x44e) & 0xFF;
				String str = String.format("%03d", progNo + 1);
				int index = 0;
				for (; index < 3; index++)
					currentDisplayedString[index] = str.charAt(index);
				if (toneNo == 0xFFFF && rhythm == null) {
					currentDisplayedString[index++] = '+';
					for (; index < 16; index++)
						currentDisplayedString[index] = '-';
				} else {
					char prefix = ' ';
					if (rhythm != null) {
						prefix = '*';
					} else if (mapNo == 119) { // XG Instrument (Custom)
						prefix  = '^';
					} else if (bankNo == 126 || bankNo == 127) { // CM-64 Instrument
						prefix = '#';
					} else if (bankNo != 0) {
						prefix = '+';
					} else if ((rxBankFlags & 1) == 0) {
						prefix = '_';
					}
					currentDisplayedString[index++] = prefix;
					if (mapNo == 1)
						currentDisplayedString[index++] = '\"';
					if (mapNo == 2)
						currentDisplayedString[index++] = '\'';
					int remaining = 16 - index;
					byte[] instruments = rhythm != null ? rhythm.getByteArray(0x500, remaining) : block.getByteArray(0x47c, remaining);
					for (int i = 0; i < remaining; i++)
						currentDisplayedString[index++] = (char) (instruments[i] & 0xFF);
//					if (currentDisplayedString[14] == ':') {
//						System.out.println(block.getByte(0x24d));
//					}
				}
				this.level = String.valueOf(block.getByte(0x3dc) & 0xFF);
				int pan = block.getByte(0x3dd) - 64;
				if (pan == -64)
					this.pan = "Rnd";
				else if (pan == 0)
					this.pan = "0";
				else
					this.pan = String.format("%c%2d", pan < 0 ? 'L' : 'R', Math.abs(pan));
				this.reverb = String.valueOf(block.getByte(0x3e3) & 0xFF);
				this.chorus = String.valueOf(block.getByte(0x3e2) & 0xFF);
				int kshift = block.getByte(0x3da) - 64;
				if (kshift == 0)
					this.kshift = "± 0";
				else
					this.kshift = String.format("%c%2d", pan < 0 ? '-' : '+', Math.abs(kshift));
				int rxChannel = block.getByte(0x3d8) & 0xFF;
				if (rxChannel >= PARTS)
					this.midich = String.format("%c--", 'A' + systemBase.getByte(part2block(selectedPart) + 3));
				else
					this.midich = String.format("%c%02d", 'A' + (rxChannel >> 4), (rxChannel & 0xF) + 1);
			}
			
			final long currentTime = System.currentTimeMillis();
			
			int[] barmap = this.SC_88PRO_LEVEL2BAR;
			int[] bars = this.bars;
			int[] barTimers = this.barTimers;
			
			for (int i = 0; i < PARTS; i++) {
				int bar = barmap[Math.max(Math.min(getBlockLevel(part2block(i)) >> 8, 127), 0)];
				bar = Math.max(Math.min(bar, 127), -1);
				if (bar >= bars[i]) {
					bars[i] = bar;
					barTimers[i] = 4;
				} else if (bars[i] >= 8 && barTimers[i]-- <= 0) {
					bars[i] -= 8;
					barTimers[i] = 4;
				}
			}
			
			if (this.currentDisplayTimer < currentTime) {
				this.currentDisplayTimer = Long.MAX_VALUE;
				this.currentDisplayedScreen = -1;
			}
			
			if (this.currentDisplayLetterTimer < currentTime) {
				if (this.displayLetter.length() <= 16) {
					this.currentDisplayLetterTimer = Long.MAX_VALUE;
					this.displayLetter = null;
				} else {
					if (this.displayLetterOffset++ == this.displayLetter.length() + 16) {
						this.displayLetter = null;
						this.currentDisplayLetterTimer = Long.MAX_VALUE;
						this.displayLetterOffset = 0;
					} else {
						this.currentDisplayLetterTimer += SCROLL_SPEED;
					}
				}
			}
			
			if (this.currentSystemMessageTimer < currentTime) {
				this.currentSystemMessageTimer = Long.MAX_VALUE;
				this.systemMessage = null;
			}
			
			if (this.displayLetter != null) {
				if (this.displayLetter.length() <= 16) {
					setCurrentDisplayedString(this.displayLetter);
				} else {
					String displayLetter = this.displayLetter;
					int displayLetterOffset = this.displayLetterOffset;
					char[] currentDisplayedString = this.currentDisplayedString;
					if (displayLetterOffset < 16) {
						System.arraycopy(currentDisplayedString, displayLetterOffset, currentDisplayedString, 0, 16 - displayLetterOffset);
						for (int i = 16 - displayLetterOffset, j = 0; i < 16; i++)
							currentDisplayedString[i] = displayLetter.charAt(j++);
					} else {
						if (displayLetterOffset <= displayLetter.length()) {
							displayLetterOffset -= 16;
							for (int i = 0; i < 16; i++)
								currentDisplayedString[i] = displayLetter.charAt(i + displayLetterOffset);
						} else {
							displayLetterOffset -= displayLetter.length();
							System.arraycopy(currentDisplayedString, 0, currentDisplayedString, 16 - displayLetterOffset, displayLetterOffset);
							for (int i = displayLetterOffset, j = 0; i < 16; i++)
								currentDisplayedString[j++] = displayLetter.charAt(i + displayLetter.length() - 16);
						}
					}
				}
			}
			if (this.systemMessage != null) {
				setCurrentDisplayedString(this.systemMessage);
			}
			
			if (this.bitmapTimer < currentTime) {
				int index = this.bitmapIndex++;
				if (index == this.bitmapEnd) {
					this.bitmapTimer = Long.MAX_VALUE;
					this.bitmapIndex = -1;
				} else {
					this.bitmapTimer = currentTime + this.bitmapDelay;
				}
				this.currentDisplayedScreen = -1;
			}
			
//			for (int i = 0; i < this.screenData.length; i++) {
//				this.screenData[i] ^= 0xFFFFFFFFFFFFFFFFL;
//			}

//			BufferStrategy buffer = this.getBufferStrategy();
//			if(buffer == null) {
//				createBufferStrategy(2);
//				buffer = this.getBufferStrategy();
//			}
//			update(buffer.getDrawGraphics());
//			buffer.show();
		}
		
		public void handleMidiMessage(byte[] data, int part) {
			if (data[1] == 0x41 /* Roland */ && data.length > 10 && data[2] == getDeviceID()) {
//				System.out.println(Util.toHex(data));
				if (SoundCanvas.checksum(data, data.length) != 0) {
					if (data[3] == 0x45)
						showSystemMessage("Check Sum Error ");
					return;
				}
				if (data[3] == 0x42 /* MODEL ID = 42H */) {
					if (data[4] == 0x12 /* Data transmission 1 */) {
						int addrH = data[5];
						int addrM = data[6];
						int addrL = data[7];
						int addr = addrH << 16 | addrM << 8 | addrL;
						if (addr == 0x00007F && data[8] != 0)
							return;
						switch(addr) {
						case 0x40007F:
						case 0x00007F: {
							reset();
							break;
						}
						}
						if (addr == 0x00007F) {
							showSystemMessage("Mode " + (data[8] + 1));
						}
//						if (addrH == 0x40 && addrM == 0x01 && addrL == 0x00) {
//							byte[] patchName = new byte[Math.min(data.length - 10, 16)];
//							System.arraycopy(data, 8, patchName, 0, patchName.length);
//							this.instrument = new String(patchName, StandardCharsets.ISO_8859_1);
//							setCurrentDisplayedString(this.instrument);
//						}
					}
				}
				if (data[3] == 0x45 /* MODEL ID = 45H */) {
					if (data[4] == 0x12 /* Data transmission 1 */) {
						int addrH = data[5];
						int addrM = data[6];
						int addrL = data[7];
						if (addrH == 0x10) {
							if (addrM == 0x00) {
								if (addrL == 0x00) { // Displayed Letter
									byte[] displayLetter = new byte[Math.min(data.length - 10, 32)];
									System.arraycopy(data, 8, displayLetter, 0, displayLetter.length);
									for (int i = 0, len = displayLetter.length; i < len; i++) {
										if (displayLetter[i] < 32)
											displayLetter[i] = 32;
									}
									showDisplayLetter(new String(displayLetter, StandardCharsets.ISO_8859_1));
								}
							}
							if (addrM > 0x00 && addrM < 0x06) { // Display dot data
								int pageNo = (addrM - 1) * 2 + addrL / 0x40;
								byte[] screenData = new byte[0x40];
								System.arraycopy(data, 8, screenData, 0, 0x40);
								long[] display = this.displayDotData[pageNo];
								Arrays.fill(display, 0x0000000000000000L);
								for (int i = 0; i < 16; i++) {
									int rowData = (screenData[i] << 11) | (screenData[i + 16]) << 6 | (screenData[i + 32] << 1) | (screenData[i + 48] >> 4);
									for (int j = 0; j < 16; j++) {
										setPixelAt(display, j, i, (rowData & (1 << (15 - j))) != 0);
									}
								}
								if (pageNo == 0) {
									this.currentDisplayedScreen = 0;
									this.currentDisplayTimer = System.currentTimeMillis() + this.displayTime * 480;
								}
							}
							if (addrM == 0x10) { // Function Control Parameter SC-55 Only
								if (addrL == 0x00) { // Select BLOCK (Part Select) 
									if (data[8] >= 0 && data[8] < PARTS) {
										this.selectedPart = block2part(data[8]);
										this.inspectAll = false;
									} else {
										this.inspectAll = true;
									}
								}
								if (addrL == 0x01) { // Minus One (Not implemented)
									
								}
								if (addrL == 0x02) { // Solo (Not implemented)
									
								}
							}
							if (addrM == 0x20) {
								if (addrL == 0x00) { // Display Page
									int pageNo = data[8];
									if (pageNo >= 0 && pageNo <= 10) {
										this.currentDisplayedScreen = pageNo - 1;
										if (pageNo != 0) {
											this.currentDisplayTimer = System.currentTimeMillis() + this.displayTime * 480;
										}
									}
								}
								if (addrL == 0x01) { // Display Time
									int time = data[8];
									if (time >= 0 && time < 16) {
										this.displayTime = time;
									}
								}
							}
						}
					}
				}
			}
		}
		
		public void reset() {
			this.displayTime = 6;
			this.currentDisplayedScreen = -1;
			this.currentDisplayTimer = this.currentDisplayLetterTimer = Long.MAX_VALUE;
			this.displayLetter = null;
			this.displayLetterOffset = 0;
			long[][] displayDotData = this.displayDotData;
			for (int i = 0; i < displayDotData.length; i++) {
				Arrays.fill(displayDotData[i], 0x0000000000000000L);
			}
		}

		public void showDisplayLetter(String str) {
			if (str.length() <= 16) {
				this.displayLetter = str;
				this.currentDisplayLetterTimer = System.currentTimeMillis() + this.displayTime * 480;
			} else {
				this.displayLetter = "<" + str + ">";
				this.currentDisplayLetterTimer = System.currentTimeMillis() + SCROLL_SPEED;
				this.displayLetterOffset = 0;
			}
		}
		
		public void showSystemMessage(String str) {
			assert str.length() <= 16;
			this.systemMessage = str;
			this.currentSystemMessageTimer = System.currentTimeMillis() + 1000;
		}

		public void setPixelAt(int x, int y, boolean on) {
			setPixelAt(this.screenData, x, y, on);
		}
		
		private void setPixelAt(long[] screenData, int x, int y, boolean on) {
			long l = screenData[y >> 2];
			long loc = 1L << (((3 - (y & 3L)) << 4L) | x);
			if(on) l |=  loc;
			else   l &= ~loc;
			screenData[y >> 2] = l;
		}

		public boolean isInspectAll() {
			return this.inspectAll;
		}

		public void setInspectAll(boolean inspectAll) {
			this.inspectAll = inspectAll;
		}
		
		public int getSelectedPart() {
			return this.selectedPart;
		}

		public void setSelectedPart(int selectedPart) {
			if (this.inspectAll)
				return;
			this.selectedPart = selectedPart;
		}

		private AffineTransform getScaledTransform(double scale) {
			AffineTransform transform = new AffineTransform();
			transform.scale(scale, scale);
			return transform;
		}

	}
	
	class TempControlPanel extends JFrame {

		private static final long serialVersionUID = -5853617061492868854L;

		public TempControlPanel() {
			setTitle("Temp control Panel");
			setDefaultCloseOperation(DISPOSE_ON_CLOSE);

			setSize(220, 180);
			setLocationRelativeTo(null);

			JPanel root = new JPanel(new GridBagLayout());
			setContentPane(root);

			JPanel centerPanel = new JPanel();
			centerPanel.setLayout(new BorderLayout(0, 10));

			JToggleButton allButton = new JToggleButton("ALL");
			allButton.setModel(new DefaultButtonModel() {
				
				private static final long serialVersionUID = 7877450539517810433L;

				@Override
				public boolean isSelected() {
					return SC88ProGui.this.canvas.isInspectAll();
				}
			});
			allButton.addActionListener(l -> SC88ProGui.this.canvas.setInspectAll(!SC88ProGui.this.canvas.isInspectAll()));
			allButton.setPreferredSize(new Dimension(120, 35));
			centerPanel.add(allButton, BorderLayout.NORTH);

			JPanel bottomPanel = new JPanel(new GridLayout(1, 2, 10, 0));

			JButton leftButton = new JButton("←");
			JButton rightButton = new JButton("→");

			leftButton.setPreferredSize(new Dimension(50, 35));
			rightButton.setPreferredSize(new Dimension(50, 35));
			
			leftButton.addActionListener(l -> SC88ProGui.this.canvas.setSelectedPart(SC88ProGui.this.canvas.getSelectedPart() - 1));
			rightButton.addActionListener(l -> SC88ProGui.this.canvas.setSelectedPart(SC88ProGui.this.canvas.getSelectedPart() + 1));

			bottomPanel.add(leftButton);
			bottomPanel.add(rightButton);

			centerPanel.add(bottomPanel, BorderLayout.CENTER);

			root.add(centerPanel);

			setVisible(true);
		}
	}

}
