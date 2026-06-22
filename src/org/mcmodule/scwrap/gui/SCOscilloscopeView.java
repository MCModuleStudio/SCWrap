package org.mcmodule.scwrap.gui;

import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import javax.swing.plaf.basic.BasicInternalFrameUI;

import org.mcmodule.scwrap.util.Oscilloscope;
import org.mcmodule.scwrap.util.SCCoreVersion;

import com.sun.jna.Pointer;

import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferStrategy;
import java.util.HashMap;

public class SCOscilloscopeView extends JPanel implements Runnable {

	private static final long serialVersionUID = 2075643121529524791L;
	private static final int[] PARTS  = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 10, 11, 12, 13, 14, 15}; 
	private static final int INTERP_MAX = 1024;
	private static final float[][] INTERP_COEFF = createInterpCoeff();

	private final int channelCount;
	private final int columnCount;
	private final JDesktopPane desktopPane;
	private final JInternalFrame[] frames;
	private final SCOscilloscopeView.OscilloscopeCanvas[] canvas;
	private final SCCoreVersion version;
	private final Pointer base;
	private final Pointer blockBase;
	private final boolean full;
	
	private Thread renderThread;
	

	public SCOscilloscopeView(SCCoreVersion version, Pointer base, boolean full) {
		super(new BorderLayout());
		this.version = version;
		this.base = base;
		this.blockBase = base.getPointer(version.getBlockBaseVariable());
		this.full = full;
		
		int channelCount;
		if (full) {
			channelCount = this.channelCount = 64;
			this.columnCount = 8;
		} else {
			channelCount = this.channelCount = 32;
			this.columnCount = 4;
		}

		JDesktopPane desktopPane = this.desktopPane = new JDesktopPane();
		add(desktopPane, BorderLayout.CENTER);
		this.frames = new JInternalFrame[channelCount];
		this.canvas = new SCOscilloscopeView.OscilloscopeCanvas[channelCount];

		createFrames();

		desktopPane.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				updateLayout();
			}
		});

		SwingUtilities.invokeLater(this::updateLayout);
	}

	private void createFrames() {
		for (int i = 0; i < channelCount; i++) {

			JInternalFrame frame = new JInternalFrame("Channel " + (i + 1), false, false, false, false);

			frame.setVisible(true);
			frame.setUI(new BasicInternalFrameUI(frame) {
				@Override
				protected MouseInputAdapter createBorderListener(JInternalFrame w) {
					return new MouseInputAdapter() {
					};
				}
			});

//			frame.setFrameIcon(null);
//			BasicInternalFrameUI ui = (BasicInternalFrameUI) frame.getUI();
//			BasicInternalFrameTitlePane north = (BasicInternalFrameTitlePane) ui.getNorthPane();
//			north.removeAll();
//			north.setPreferredSize(new Dimension(0, 18));

			OscilloscopeCanvas canvas = this.canvas[i] = new OscilloscopeCanvas();

			frame.setLayout(new BorderLayout());
			frame.add(canvas, BorderLayout.CENTER);

			frames[i] = frame;
			desktopPane.add(frame);
		}
	}

	private void updateLayout() {

		int width = desktopPane.getWidth();
		int height = desktopPane.getHeight();

		if (width <= 0 || height <= 0) {
			return;
		}

		int rows = (channelCount + columnCount - 1) / columnCount;

		int cellWidth = width / columnCount;
		int cellHeight = height / rows;

		for (int i = 0; i < channelCount; i++) {

			int row = i / columnCount;
			int col = i % columnCount;

			int x = col * cellWidth;
			int y = row * cellHeight;

			int w = (col == columnCount - 1) ? width - x : cellWidth;

			int h = (row == rows - 1) ? height - y : cellHeight;

			frames[i].setBounds(x, y, w, h);
		}
	}
	
	@Override
	public void addNotify() {
		super.addNotify();
		this.renderThread = new Thread(this);
		this.renderThread.setName("Gui Thread");
		this.renderThread.setPriority(Thread.MIN_PRIORITY);
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
		Thread currentThread = Thread.currentThread();
		while (!currentThread.isInterrupted()) {
			synchronized (this) {
				this.notifyAll();
			}
			try {
				Thread.sleep(1000L / 30L);
			} catch (InterruptedException e) {
				break;
			}
		}	
	}

	class OscilloscopeCanvas extends Canvas implements Runnable {
		private static final long serialVersionUID = -603359823739169081L;
		private static final int BUFFER_SIZE = 1024;
		private final Oscilloscope oscilloscope = new Oscilloscope(BUFFER_SIZE, BUFFER_SIZE / 4 * 3);
		private final float[] buffer = new float[BUFFER_SIZE];
		
		private Thread renderThread;
		
		@Override
		public void addNotify() {
			super.addNotify();
			createBufferStrategy(2);
			this.renderThread = new Thread(this);
			this.renderThread.setName("Gui Thread");
			this.renderThread.setPriority(Thread.MIN_PRIORITY);
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
			BufferStrategy bs = getBufferStrategy();
			if (bs != null) {
				bs.dispose();
			}
			super.removeNotify();
		}
		
		@Override
		public void paint(Graphics g) {

			int w = getWidth();
			int h = getHeight();
			int halfH = h >> 1;

			g.setColor(Color.BLACK);
			g.fillRect(0, 0, w, h);

			g.setColor(Color.GREEN);
			
			if (!EventQueue.isDispatchThread()) {
				this.oscilloscope.compute(this.buffer, w, h);
			} else return;
			
			int prevX = 0;
			int prevY = 0;
			
			float[] output = this.oscilloscope.output;
			for (int i = 0, len = output.length; i < w; i++) {
				float out = cubicSample(output, (i / (float)w) * len) * 4f;
				int y = Math.max(Math.min((int) Math.round(-out * halfH), +halfH), -halfH) + halfH;
				if (i != 0) {
					g.drawLine(prevX, prevY, i, y);
				}
				prevX = i;
				prevY = y;
			}
		}

		public void pushBuffer(float[] buffer) {
			int length = buffer.length;
			System.arraycopy(this.buffer, length, this.buffer, 0, this.buffer.length - length);
			System.arraycopy(	 buffer, 0	 , this.buffer, this.buffer.length - length, length);
		}

		@Override
		public void run() {
			Thread currentThread = Thread.currentThread();
			while (!currentThread.isInterrupted()) {
				BufferStrategy bs = getBufferStrategy();
				if (bs != null) {
					do {
						do {
							Graphics g = bs.getDrawGraphics();
							try {
								paint(g);
							} finally {
								g.dispose();
							}
						} while (bs.contentsRestored());
						bs.show();
						Toolkit.getDefaultToolkit().sync();
					} while (bs.contentsLost());
				}
				synchronized (SCOscilloscopeView.this) {
					try {
						SCOscilloscopeView.this.wait();
					} catch (InterruptedException e) {
						currentThread.interrupt();
						break;
					}
				}
			}
		}
	}

	public void update() {
		boolean full = this.full;
		SCOscilloscopeView.OscilloscopeCanvas[] canvases = this.canvas;
		Pointer base = this.base;
		Pointer channelBase = new Pointer(Pointer.nativeValue(base) + this.version.getMixerBufferVariable());
		for (int i = 0, len = this.channelCount; i < len; i++) {
			SCOscilloscopeView.OscilloscopeCanvas canvas = canvases[i];
			int offset;
			if (full)
				offset = 32 * 4 * i;
			else
				offset = 32 * 4 * (16 + part2block(i));
			canvas.pushBuffer(channelBase.getFloatArray(offset, 32));
		}
		if (full)
			updateTitlesFull();
		else
			updateTitlesPart();
	}
	
	private void updateTitlesFull() {
		JInternalFrame[] frames = this.frames;
		HashMap<JInternalFrame,String> toChange = null;
		for (int i = 0, len = this.channelCount; i < len; i++) {
			String title = String.format("Channel %d %s", i + 1, getTitleFull(i));
			JInternalFrame frame = frames[i];
			if (!title.equals(frame.getTitle())) {
				if (toChange == null) {
					toChange = new HashMap<>();
				}
				toChange.put(frame, title);
			}
		}
		if (toChange != null) {
			HashMap<JInternalFrame,String> toChange2 = toChange; // WHY???? ORACLE?????
			EventQueue.invokeLater(() -> toChange2.forEach(JInternalFrame::setTitle));
		}
	}
	
	private void updateTitlesPart() {
		JInternalFrame[] frames = this.frames;
		HashMap<JInternalFrame,String> toChange = null;
		for (int i = 0, len = this.channelCount; i < len; i++) {
			String title = String.format("Channel %d %s", i + 1, getTitlePart(i));
			JInternalFrame frame = frames[i];
			if (!title.equals(frame.getTitle())) {
				if (toChange == null) {
					toChange = new HashMap<>();
				}
				toChange.put(frame, title);
			}
		}
		if (toChange != null) {
			HashMap<JInternalFrame,String> toChange2 = toChange; // WHY???? ORACLE?????
			EventQueue.invokeLater(() -> toChange2.forEach(JInternalFrame::setTitle));
		}
	}
	
	private String getTitleFull(int ch) {
		switch (ch) {
		case 0:
		case 1:
			return "Master Output";

		case 2:
			return "Chorus Send";

		case 3:
			return "Delay Send";

		case 4:
		case 5:
			return "Chorus Return";

		case 6:
		case 7:
			return "Delay Return";

		case 8:
		case 9:
			return "Reverb Return";

		case 10:
		case 11:
			return "EQ Return";

		case 12:
		case 13:
		case 14:
			return "EFX Return";

		case 16:
		case 17:
		case 18:
		case 19:
		case 20:
		case 21:
		case 22:
		case 23:
		case 24:
		case 25:
		case 26:
		case 27:
		case 28:
		case 29:
		case 30:
		case 31:
		case 32:
		case 33:
		case 34:
		case 35:
		case 36:
		case 37:
		case 38:
		case 39:
		case 40:
		case 41:
		case 42:
		case 43:
		case 44:
		case 45:
		case 46:
		case 47:
			return getTitleBlock(ch - 16);

		case 49:
		case 50:
			return "Secondary Output";

		case 51:
		case 52:
			return "EQ Send";

		case 58:
		case 59:
			return "EQ Off Parts";

		case 60:
			return "Reverb Send";

		case 62:
		case 63:
			return "EFX Send";

		default:
			return "";
		}
	}

	private String getTitlePart(int ch) {
		return getTitleBlock(part2block(ch));
	}

	private String getTitleBlock(int ch) {
		Pointer rhythm = this.blockBase.getPointer(1160 * ch + 0x18);
		String title = new String(rhythm == null ? this.blockBase.getByteArray(1160 * ch + 1148, 12) : rhythm.getByteArray(0x500, 12));
		if (this.blockBase.getByte(1160 * ch + 0x452) != 0)
			title += " EFX";
		return title;
	}

	protected static int part2block(int partNo) {
		return PARTS[partNo & 0xF] | (partNo &~0xF);
	}
	
	private static float[][] createInterpCoeff() {
		float[][] coeff = new float[INTERP_MAX][4];
		for (int i = 0; i < INTERP_MAX; i++) {
			float x = (float) i / INTERP_MAX;
			coeff[i][0] = x * (-0.5f + x * (1f - 0.5f * x));
			coeff[i][1] = 1.0f + x * x * (1.5f * x - 2.5f);
			coeff[i][2] = x * (0.5f + x * (2.0f - 1.5f * x));
			coeff[i][3] = 0.5f * x * x * (x - 1.0f);
		}
		return coeff;
	}

	private static float cubicSample(float[] array, float index) {
		int len = array.length;
		int idx = (int) index;
		int fract = (int) ((index - idx) * INTERP_MAX);

		if (fract == INTERP_MAX) {
			fract = 0;
			idx++;
		}

		float[] c = INTERP_COEFF[fract];

		return sample(array, idx - 1, len) * c[0]
			 + sample(array, idx,	 len) * c[1]
			 + sample(array, idx + 1, len) * c[2]
			 + sample(array, idx + 2, len) * c[3];
	}

	private static float sample(float[] array, int idx, int len) {
		if (idx == -1) idx = 0;
		if (idx >= len && idx <= len + 3) idx = len - 1;
		return (idx < 0 || idx >= len) ? 0f : array[idx];
	}

}