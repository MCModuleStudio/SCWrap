package org.mcmodule.scwrap;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HMODULE;

public class Main {
	
	private static final String DEFAULT_PORT_NAME = "Roland SC-VA";
	private static int portIndex = -1;
	private static boolean midiTxNoDelay = false;
	
	public static void main(String[] args) {
		try {
			main0(args);
		} catch (Throwable e) {
			Thread currentThread = Thread.currentThread();
			currentThread.getUncaughtExceptionHandler().uncaughtException(currentThread, e);
			System.exit(-1);
		}
	}

	public static void main0(String[] args) throws Throwable {
		int sampleRate = 32000;
		int blockSize = 32;
		String libraryPath = "SCCore.dll";
		String midiA = null, midiB = null, midiOut = null;
		String output = null;
		int map = 4;
		boolean guiEnabled = false;
		int instances = 1;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "--rate":
				case "-r":
					if (i + 1 < args.length) sampleRate = Integer.parseInt(args[++i]);
					else System.err.println("Missing value for --rate");
					break;
				case "--block":
				case "-s":
					if (i + 1 < args.length) blockSize = Integer.parseInt(args[++i]);
					else System.err.println("Missing value for --block");
					break;
				case "--lib":
				case "-l":
					if (i + 1 < args.length) libraryPath = args[++i];
					else System.err.println("Missing value for --lib");
					break;
				case "--partA":
				case "--midiA":
				case "--midi":
				case "-a":
					if (i + 1 < args.length) midiA = args[++i];
					else System.err.println("Missing value for --midiA");
					break;
				case "--partB":
				case "--midiB":
				case "-b":
					if (i + 1 < args.length) midiB = args[++i];
					else System.err.println("Missing value for --midiB");
					break;
				case "--midiOut":
				case "-p":
					if (i + 1 < args.length) midiOut = args[++i];
					else System.err.println("Missing value for --midiOut");
					break;
				case "--output":
				case "-o":
					if (i + 1 < args.length) output = args[++i];
					else System.err.println("Missing value for --output");
					break;
				case "--map":
				case "-m":
					if (i + 1 < args.length) {
						map = parseMap(args[++i].toLowerCase());
						if (map < 1 || map > 4) {
							System.err.println("Unknown map type " + args[i]);
							map = 4;
						}
					} else System.err.println("Missing value for --map");
					break;
				case "--inst":
				case "-i":
					if (i + 1 < args.length) instances = Integer.parseInt(args[++i]);
					else System.err.println("Missing value for --inst");
					break;
				case "--help":
				case "-h":
					System.out.println("Usage:");
					System.out.println("  -r, --rate            <sample rate>   Set sample rate (default: 32000)");
					System.out.println("  -s, --block            <block size>   Set block size (default: 32)");
					System.out.println("  -l, --lib            <library path>   Set SCCore.dll library path (default: SCCore.dll)");
					System.out.println("  -a, --midiA, -midi <port name/file>   Set MIDI input A");
					System.out.println("  -b, --midiB        <port name/file>   Set MIDI input B");
					System.out.println("  -p, --midiOut           <port name>   Set MIDI output");
					System.out.println("  -o, --output            <file name>   Set audio output file");
					System.out.println("  -m, --map                <map type>   Set map type");
					System.out.println("  -i, --inst              <instances>   Set instance nummber");
					System.out.println("      --gui                             Open gui");
					return;
				case "--gui":
					guiEnabled = true;
					break;
				default:
					System.err.println("Unknown option: " + args[i]);
					break;
			}
		}

		SoundCanvas sc = instances <= 1 ? new SoundCanvas(libraryPath, sampleRate, blockSize) : new MultiInstancedSoundCanvas(new File(libraryPath), sampleRate, blockSize, instances);
		SCCoreVersion version = SCCoreVersion.identifyVersion(libraryPath);
		HMODULE tgModule = null;
		if (version != null) {
			System.out.printf("SCCore version: %s (%s)\n", version.getInternalVersion(), version.name());
			tgModule = Kernel32.INSTANCE.GetModuleHandle(libraryPath);
		} else {
			System.out.println("Unable to identify version.");
		}
		
		AbstractGui frame = null;
		if (guiEnabled && instances <= 1) {
			if (version != null) {
//				FIXME
//				try {
//					UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
//				} catch (ClassNotFoundException | InstantiationException | IllegalAccessException
//						| UnsupportedLookAndFeelException e) {
//					e.printStackTrace();
//				}
				frame = new SYXG50Gui(sc, tgModule, version);
			} else {
				System.out.println("Gui require supported SCCore version!");
			}
		}
		final AbstractGui gui = frame;
		
		if (map != 4) {
			sc.changeMap(map);
		}
		
		AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, sampleRate, 16, 2, 4, sampleRate, true);
		SourceDataLine line = AudioSystem.getSourceDataLine(format);
		line.open(format, 4096);
		line.start();
		
		RandomAccessFile file = null;
		if (output != null) {
			file = new RandomAccessFile(new File(output), "rw");
			byte[] header = new byte[44];
			ByteBuffer byteBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			byteBuffer.putInt(0x46464952); // RIFF
			byteBuffer.putInt(0);
			byteBuffer.putInt(0x45564157); // WAVE
			byteBuffer.putInt(0x20746D66); // fmt 
			byteBuffer.putInt(16);
			byteBuffer.putShort((short) 3);
			byteBuffer.putShort((short) 2);
			byteBuffer.putInt(sampleRate);
			byteBuffer.putInt(sampleRate * Float.BYTES * 2);
			byteBuffer.putShort((short) (Float.BYTES * 2));
			byteBuffer.putShort((short) Float.SIZE);
			byteBuffer.putInt(0x61746164); // data
			byteBuffer.putInt(0);
			file.write(header);
		}
		
		Info[] midiInDevice = getMidiInDevice();
		Info[] midiOutDevice = getMidiOutDevice();
		System.out.println("MIDI in devices:");
		for (int i = 0; i < midiInDevice.length; i++) {
			Info info = midiInDevice[i];
			System.out.printf("%d. %s\n", i, info);
		}
		System.out.println("MIDI out devices:");
		for (int i = 0; i < midiOutDevice.length; i++) {
			Info info = midiOutDevice[i];
			System.out.printf("%d. %s\n", i, info);
		}
		Sequencer sequencerA = null, sequencerB = null;
		Receiver receiver = null;
		sequencerA = openMidiInDeviceOrMidiFile(sc, midiInDevice, midiA, 0);
		
		sequencerB = openMidiInDeviceOrMidiFile(sc, midiInDevice, midiB, 1);
		
		if ((midiOut != null) || (midiA == null && midiB == null && version != null) && instances <= 1) {
			if (version != null) {
				// Fix bulk dump cause softlock
				// This is a temporary fix. It's better to move MIDI output to other thread.
				// Because SC-8820 uses interrupt to handle MIDI output. When buffer full, SC-8820 blocks mainloop until buffer is free.
				// But SCVA doesn't have interrupts, it handle MIDI output after mainloop, but mainloop was blocking wait for buffer free, so buffer never can be free when it's full. finally cause softlock.
				// What suck code! Why Roland dose not use coroutine to simulate interrupts?
				int newLength = 65535;
				Pointer ptr = new Pointer(Pointer.nativeValue(tgModule.getPointer().getPointer(version.getEventBufferQueueVariable())) + 192 * 2);
				Native.free(ptr.getLong(0L));
				ptr.setLong(0, Native.malloc(newLength * Integer.BYTES));
				ptr.setShort(12, (short) newLength);
				receiver = openMidiOutDevice(sc, midiOutDevice, midiOut);
			} else {
				System.out.println("MIDI output require supported SCCore version!");
			}
		}
		
		if (gui != null)
			EventQueue.invokeLater(gui::open);
		
		if (sequencerA != null)
			sequencerA.start();
		
		if (sequencerB != null)
			sequencerB.start();
		
		PacketDecoder packetDecoder = new PacketDecoder();
		float[] out = new float[blockSize << 1];
		byte[] byteArray = new byte[blockSize << 2];
		byte[] byteArray2 = new byte[blockSize << 3];
		ByteBuffer byteBuffer = ByteBuffer.wrap(byteArray2).order(ByteOrder.LITTLE_ENDIAN);
		long stopTime = -1;
		long frames = 0;
		int readerIndex = 0;
		long nextMidiTransmitTime = 0L;
		long currentTime;
		for (;;) {
			currentTime = System.currentTimeMillis();
			Arrays.fill(out, 0f);
			sc.process(out);
			if (gui != null)
				gui.process(out);
			frames += blockSize;
			toByteArray(out, byteArray);
			line.write(byteArray, 0, byteArray.length);
			if (file != null) {
				byteBuffer.clear();
				for (int i = 0, len = out.length; i < len; i++) {
					byteBuffer.putFloat(out[i]);
				}
				file.write(byteArray2);
			}
			if (version != null) {
				if (receiver != null && (currentTime >= nextMidiTransmitTime || midiTxNoDelay)) {
					Pointer ptr = new Pointer(Pointer.nativeValue(tgModule.getPointer().getPointer(version.getEventBufferQueueVariable())) + 192 * 2);
					int writerIndex = ptr.getShort(10) & 0xFFFF;
					int length      = ptr.getShort(12) & 0xFFFF;
					while (readerIndex != writerIndex) {
						int result = ptr.getPointer(0).getInt(readerIndex++ * Integer.BYTES);
						if (readerIndex == length)
							readerIndex = 0;
						if ((result & 0xFF) != 0) {
							byte[] decodedMessage = packetDecoder.decodeMessage(result);
							if (decodedMessage != null) {
								if ((decodedMessage[0] & 0xFF) == 0xF0) {
									receiver.send(new SysexMessage(decodedMessage, decodedMessage.length), 0L);
								} else {
									receiver.send(new ShortMessage(decodedMessage[0] & 0xFF, decodedMessage[1] & 0xFF, decodedMessage.length > 2 ? decodedMessage[2] & 0xFF : 0), 0L);
								}
								if (midiTxNoDelay)
									continue;
								// Add a little delay because Java's built-in MIDI system have really bad implementation.
								// It will hang up program or stop working when large amount data transmit.
								// For currently, It emulates MIDI connection baud rate (3.125kbps)
								// Although transmit speed has been limited to 3125 bytes per second, it still faster than real machine
								nextMidiTransmitTime = currentTime + (decodedMessage.length * 1000) / 3125;
								break;
							}
						}
					}
				}
			}
			if (sequencerA != null || sequencerB != null) {
				boolean playing = false;
				if (sequencerA != null)
					playing |=  sequencerA.isRunning();
				if (sequencerB != null)
					playing |=  sequencerB.isRunning();
				if (!playing && stopTime < 0)
					stopTime = System.currentTimeMillis() + 10000L; // Stop after 10 seconds;
			} else {
				if (file != null) {
					long filePointer = file.getFilePointer();
					file.seek(4L);
					file.writeInt(Integer.reverseBytes((int) (filePointer - 8)));
					file.seek(40L);
					file.writeInt(Integer.reverseBytes((int) (frames << 3)));
					file.seek(filePointer);
				}
			}
			if (stopTime > 0 && System.currentTimeMillis() > stopTime)
				break;
			if (gui != null && !gui.isDisplayable())
				break;
		}
		if (sequencerA != null)
			sequencerA.close();
		
		if (sequencerB != null)
			sequencerB.close();
		
		line.close();
		
		if (file != null) {
			long filePointer = file.getFilePointer();
			file.seek(4L);
			file.writeInt(Integer.reverseBytes((int) (filePointer - 8)));
			file.seek(40L);
			file.writeInt(Integer.reverseBytes((int) (frames << 3)));
			file.close();
		}
		for (int i = 0, len = midiInDevice.length; i < len; i++) {
			Info info = midiInDevice[i];
			try {
				MidiSystem.getMidiDevice(info).close();
			} catch (MidiUnavailableException e) {
				e.printStackTrace();
			}
		}
		for (int i = 0, len = midiOutDevice.length; i < len; i++) {
			Info info = midiOutDevice[i];
			try {
				MidiSystem.getMidiDevice(info).close();
			} catch (MidiUnavailableException e) {
				e.printStackTrace();
			}
		}
	}

	private static int parseMap(String mapType) {
		if (mapType.indexOf('8') >= 0) {
			if (mapType.indexOf('5') >= 0 || mapType.indexOf('2') >= 0) {
				return 4;
			}
			if (mapType.indexOf('p') >= 0) {
				return 3;
			}
			return 2;
		} else if (mapType.indexOf('5') >= 0) {
			return 1;
		}
		try {
			return Integer.valueOf(mapType);
		} catch (NumberFormatException e) {}
		return 0;
	}

	private static Sequencer openMidiInDeviceOrMidiFile(SoundCanvas sc, Info[] midiInDevice, String name, int portNo) {
		if (name != null && name.toLowerCase().endsWith(".mid")) {
			try { // Since Java default sequencer cannot support 2 ports, we need split into 2 midi files.
				Sequence sequence = MidiSystem.getSequence(new File(name));
				Sequencer sequencer = MidiSystem.getSequencer(false);
				sequencer.open();
				sequencer.setSequence(sequence);
				sequencer.getTransmitter().setReceiver(sc.createReceiver(portNo));
				return sequencer;
			} catch (InvalidMidiDataException | IOException | MidiUnavailableException e) {
				e.printStackTrace();
			}
		} else openMidiInDevice(sc, midiInDevice, name, portNo);
		return null;
	}

	private static void openMidiInDevice(SoundCanvas sc, Info[] midiInDevice, String name, int portNo) {
		if (name == null) {
			if (TeVirtualMIDIWrap.isSupported()) {
				try {
					createInport(sc, name, portNo);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			return;
		}
		Info info = findMidiDevice(midiInDevice, name);
		if (info == null) {
			if (TeVirtualMIDIWrap.isSupported()) {
				try {
					createInport(sc, name, portNo);
					return;
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			System.err.printf("No such device: %s\n", name);
			return;
		}
		System.out.printf("Open midi device %c: %s\n", 'A' + portNo, info.getName());
		MidiDevice midiDevice;
		try {
			midiDevice = MidiSystem.getMidiDevice(info);
			midiDevice.open();
			midiDevice.getTransmitter().setReceiver(sc.createReceiver(portNo));
		} catch (MidiUnavailableException e) {
			System.err.printf("Unable open midi device %c: %s\n", 'A' + portNo, info.getName());
			e.printStackTrace();
		}
	}
	
	private static Receiver openMidiOutDevice(SoundCanvas sc, Info[] midiOutDevice, String name) {
		if (name == null) {
			if (TeVirtualMIDIWrap.isSupported()) {
				try {
					return createOutport(sc, name);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
		}
		Info info = findMidiDevice(midiOutDevice, name);
		if (info == null) {
			if (TeVirtualMIDIWrap.isSupported()) {
				try {
					return createOutport(sc, name);
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			System.err.printf("No such device: %s\n", name);
			return null;
		}
		System.out.printf("Open midi out device: %s\n", info.getName());
		MidiDevice midiDevice;
		try {
			midiDevice = MidiSystem.getMidiDevice(info);
			midiDevice.open();
			return midiDevice.getReceiver();
		} catch (MidiUnavailableException e) {
			System.err.printf("Unable open midi out device: %s\n", info.getName());
			e.printStackTrace();
		}
		return null;
	}

	private static void createInport(SoundCanvas sc, String name, int portNo) {
		if (portIndex == -1 && name == null)
			portIndex = ensurePortIndex();
		if (name == null)
			name = String.format("%s PART %c", getPortName(portIndex), 'A' + portNo);
		TeVirtualMIDIWrap virtualMidiPort = TeVirtualMIDIWrap.createVirtualMidiPort(name, 5);
		Thread thread = new Thread(() -> {
			while (true) {
				byte[] command = virtualMidiPort.getCommand();
				sc.postMidi(portNo, command);
			}
		});
		thread.setDaemon(true);
		thread.start();
	}
	
	private static Receiver createOutport(SoundCanvas sc, String name) {
		if (portIndex == -1 && name == null)
			portIndex = ensurePortIndex();
		if (name == null)
			name = getPortName(portIndex);
		TeVirtualMIDIWrap virtualMidiPort = TeVirtualMIDIWrap.createVirtualMidiPort(name, 9);
		midiTxNoDelay = true;
		return virtualMidiPort;
	}

	private static String getPortName(int portIndex) {
		return portIndex <= 1 ? DEFAULT_PORT_NAME : portIndex + "- " + DEFAULT_PORT_NAME;
	}

	private static int ensurePortIndex() {
		// How did we reach this???
		assert TeVirtualMIDIWrap.isSupported();
		int portIndex = 1;
		TeVirtualMIDIWrap in1, in2, out;
		do {
			String portName = getPortName(portIndex);
			in1 = in2 = out = null;
			try {
				in1 = TeVirtualMIDIWrap.createVirtualMidiPort(portName + " PART A", 5);
				in2 = TeVirtualMIDIWrap.createVirtualMidiPort(portName + " PART B", 5);
				out = TeVirtualMIDIWrap.createVirtualMidiPort(portName, 9);
			} catch (RuntimeException e) {
				if ("The name for the MIDI-port you specified is already in use!".equals(e.getMessage())) {
					portIndex++;
					continue;
				}
				throw e;
			} finally {
				if (in1 != null)
					in1.close();
				if (in2 != null)
					in2.close();
				if (out != null)
					out.close();
			}
			break;
		} while (true);
		return portIndex;
	}

	private static Info findMidiDevice(Info[] midiDevice, String name) {
		for (int i = 0, len = midiDevice.length; i < len; i++) {
			Info info = midiDevice[i];
			if (info.getName().equalsIgnoreCase(name))
				return info;
		}
		return null;
	}

	private static byte[] toByteArray(float[] in, byte[] out) {
		for (int i = 0, j = 0, len = in.length; i < len; i++) {
			int value = (int) ((in[i]) * 32768f);
			if (value >  32767)
				value =  32767;
			if (value < -32768)
				value = -32768;
			out[j++] = (byte) (value >>  8);
			out[j++] = (byte) (value >>  0);
		}
		return out;
	}
	
	public static Info[] getMidiInDevice() {
		ArrayList<Info> devices = new ArrayList<>();
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (Info info : infos) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if(device.getTransmitter() != null) {
					devices.add(info);
				}
			} catch (MidiUnavailableException e) {}
		}
		return devices.toArray(new Info[0]);
	}
	
	public static Info[] getMidiOutDevice() {
		ArrayList<Info> devices = new ArrayList<>();
		Info[] infos = MidiSystem.getMidiDeviceInfo();
		for (Info info : infos) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				if (device.getReceiver() != null) {
					devices.add(info);
				}
			} catch (MidiUnavailableException e) {}
		}
		return devices.toArray(new Info[0]);
	}
}
