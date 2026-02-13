package org.mcmodule.scwrap.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.mcmodule.scwrap.SoundCanvas;

// Used in test mode
class TestModeDisplay extends JFrame {
	
	private static final long serialVersionUID = 7389257732952450850L;
	private static final int SCALER = 4;
	private byte[] displayData = new byte[0x80 * 0x10];
	
	public TestModeDisplay() {
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new JPanel() {
			private static final long serialVersionUID = 2216702567745155344L;
			private final BufferedImage img;
			{
				BufferedImage img = this.img = new BufferedImage(160, 64, BufferedImage.TYPE_INT_ARGB);
				setPreferredSize(new Dimension(img.getWidth() * SCALER, img.getHeight() * SCALER));
			}
			
			@Override
			public void paint(Graphics g) {
				byte[] displayData = TestModeDisplay.this.displayData;
				BufferedImage img = this.img;
				for (int y = 0; y < 64; y++) {
					int idx = (y >> 2) * 128 + (y & 3) * 27;
					for (int x = 0; x < 160; x++) {
						int data = displayData[idx + x / 6];
						int pos = 1 << (5 - (x % 6));
						img.setRGB(x, y, (data & pos) != 0 ? 0xFF000000 : 0xFFFFFFFF);
					}
				}
				g.drawImage(img, 0, 0, this.getWidth(), this.getHeight(), this);
			}
		}, BorderLayout.CENTER);
		pack();
		setResizable(false);
		setTitle("Message from SC-8820");
		setLocationByPlatform(true);
		setVisible(true);
	}
	
	public TestModeDisplay(TestModeDisplay old) {
		this();
		if (old != null)
			System.arraycopy(old.displayData, 0, this.displayData, 0, this.displayData.length);
	}
	
	public void parse(byte[] sysex) {
		if (sysex.length < 10)
			return;
		if (SoundCanvas.checksum(sysex, sysex.length) != 0)
			return;
		if ((sysex[0] & 0xFF) != 0xF0 || sysex[1] != 0x41 || sysex[2] != 0x10  || sysex[3] != 0x45 || sysex[4] != 0x12)
			return;
		int addrH = sysex[5];
		int addrM = sysex[6];
		int addrL = sysex[7];
		if (addrH != 0x20)
			return;
		int addr = addrM * 128 + addrL;
		if (addr >= this.displayData.length)
			return;
		System.arraycopy(sysex, 8, this.displayData, addr, Math.min(sysex.length - 10, this.displayData.length - addr));
		if (addrM == 0xF)
			try {
				SwingUtilities.invokeAndWait(this::repaint);
			} catch (InvocationTargetException | InterruptedException e) {
				e.printStackTrace();
			}
	}
}