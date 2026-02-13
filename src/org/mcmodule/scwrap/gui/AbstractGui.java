package org.mcmodule.scwrap.gui;

import java.util.Arrays;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import org.mcmodule.scwrap.SoundCanvas;
import org.mcmodule.scwrap.util.SCCoreVersion;

import com.sun.jna.Function;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.WinDef.HMODULE;

public abstract class AbstractGui extends JFrame {
	
	private static final int[] BLOCKS = new int[] {9, 0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15};
	private static final int[] PARTS  = new int[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 10, 11, 12, 13, 14, 15}; 
	
	private static final long serialVersionUID = 2289565468778178946L;
	protected final SoundCanvas sc;
	protected final HMODULE tgModule;
	protected final SCCoreVersion version;
	
	private Function getPortLevel;
	protected Pointer blockBase;
	protected Pointer patchBase;
	protected Pointer setupBase;
	protected Pointer systemBase;
	private int[] readerIndex = new int[6];

	public AbstractGui(SoundCanvas sc, HMODULE tgModule, SCCoreVersion version) {
		this.sc = sc;
		this.tgModule = tgModule;
		this.version = version;
		this.getPortLevel = Function.getFunction(new Pointer(Pointer.nativeValue(tgModule.getPointer()) + version.getGetPortLevelFunction()));
		this.blockBase = tgModule.getPointer().getPointer(version.getBlockBaseVariable());
		this.patchBase = new Pointer(Pointer.nativeValue(tgModule.getPointer()) + version.getPatchVariable());
		this.setupBase = new Pointer(Pointer.nativeValue(tgModule.getPointer()) + version.getSetupVariable());
		this.systemBase = new Pointer(Pointer.nativeValue(tgModule.getPointer()) + version.getSystemVariable());
		Arrays.fill(this.readerIndex, 0);
	}
	
	
	public abstract void process(float[] samples);
	
	// FIXME
	protected static int level2bar(int level) {
		int i;
		int j;

		if (level < 512) {
			i = 0;
			j = 4;
		} else if (level < 2048) {
			i = 1;
			j = 4;
		} else if (level < 4096) {
			i = 1;
			j = 3;
		} else if (level < 6144) {
			i = 1;
			j = 2;
		} else if (level < 8192) {
			i = 1;
			j = 1;
		} else if (level < 10240) {
			i = 2;
			j = 4;
		} else if (level < 12288) {
			i = 2;
			j = 3;
		} else if (level < 14336) {
			i = 2;
			j = 2;
		} else if (level < 16384) {
			i = 2;
			j = 1;
		} else if (level < 18432) {
			i = 3;
			j = 4;
		} else if (level < 20480) {
			i = 3;
			j = 3;
		} else if (level < 22528) {
			i = 3;
			j = 2;
		} else if (level >= 24576) {
			i = 4;
			if (level < 26624) {
				j = 4;
			} else if (level < 28672) {
				j = 3;
			} else if (level < 30720) {
				j = 2;
			} else {
				j = 1;
			}
		} else {
			i = 3;
			j = 1;
		}

//		return i * 4 - j; // Guesswork
		return i * 4 + (4 - j) - 2;
//		return level >> 11;
	}
	
	protected int getPortLevel(int port) {
		return this.getPortLevel.invokeInt(new Object[] {port});
	}
	
	protected int getBlockLevel(int block) {
		Pointer listPtr = new Pointer(Pointer.nativeValue(this.blockBase) + 1160 * block + 624).getPointer(0);

		long max = 0L;

		while (listPtr != null) {
			Pointer node = listPtr.getPointer(0);

			while (node != null) {
				long val1 = node.getInt(172) & 0xFFFFFFFFL;
				long val2 = node.getInt(156) & 0xFFFFFFFFL;
				long currentVal = (val1 >> 2) * (val2 >> 2);

				if (currentVal >= 0x3FFFFFFF) {
					return 0x7FFF;
				}

				if (currentVal > max) {
					max = currentVal;
				}

				node = node.getPointer(264);
			}

			listPtr = listPtr.getPointer(32);
		}

		return (int) (max >>> 15);
	}
	
	protected int getBlockVoiceCount(int block) {
		int voices = 0;
		Pointer listPtr = new Pointer(Pointer.nativeValue(this.blockBase) + 1160 * block + 624).getPointer(0);
		while (listPtr != null) {
			Pointer node = listPtr.getPointer(0);
			while (node != null) {
				node = node.getPointer(264);
				voices++;
			}
			listPtr = listPtr.getPointer(32);
		}
		return voices;
	}
	
	protected void sendButtonEvent(Button button) {
		Pointer base = this.tgModule.getPointer();
		long buttonQueueVariable = this.version.getButtonQueueVariable();
		long eventFlagVariable = this.version.getEventFlagVariable();
		int writeIndex = base.getShort(buttonQueueVariable + 2) & 0xFFFF;
		base.setShort(buttonQueueVariable + 4 + writeIndex * 2, (short) button.ordinal()); 
		writeIndex = (writeIndex + 1) & 0x3F;
		base.setShort(buttonQueueVariable + 2, (short) writeIndex);
//		System.out.println(base.getShort(buttonQueueVariable + 0) & 0xFFFF);
		base.setInt(eventFlagVariable, base.getInt(eventFlagVariable) | 4);
	}
	
	protected int readEventQueue(int queue) {
		Pointer ptr = new Pointer(Pointer.nativeValue(this.tgModule.getPointer().getPointer(this.version.getEventBufferQueueVariable())) + 192 * queue);
		int readerIndex = this.readerIndex[queue];
//		int readerIndex = ptr.getShort( 8) & 0xFFFF;
		int writerIndex = ptr.getShort(10) & 0xFFFF;
		int length      = ptr.getShort(12) & 0xFFFF;
		if (readerIndex != writerIndex) {
			int result = ptr.getPointer(0).getInt(readerIndex++ * 4);
			if (readerIndex == length)
				readerIndex = 0;
			this.readerIndex[queue] = readerIndex;
//			ptr.setShort(8, (short)   readerIndex);
			return result;
		}
		return 0;
	}
	
	protected byte getDeviceID() {
		return this.systemBase.getByte(0x36);
	}
	
	protected Map getMap() {
		return Map.values()[this.tgModule.getPointer().getByte(this.version.getMapVariable())];
	}
	
	protected static int block2part(int blockNo) {
		return BLOCKS[blockNo & 0xF] | (blockNo &~0xF);
	}
	
	protected static int part2block(int partNo) {
		return PARTS[partNo & 0xF] | (partNo &~0xF);
	}
	
	static {
		ImageIO.setUseCache(false);
	}
	
	protected static enum Button {
		NONE,
		PREVIEW_RELEASED,
		PREVIEW_PRESSED,
		INSTMAP_RELEASED,
		INSTMAP_PRESSED;
	}
	
	protected static enum Map {
		SELECTED("Selected"),
		SC55("SC-55"),
		SC88("SC-88"),
		SC88PRO("SC-88 Pro"),
		SC8820("SC-8820");

		private final String name;

		Map(String name) {
			this.name = name;
		}

		public String getName() {
			return this.name;
		}
	}
}
