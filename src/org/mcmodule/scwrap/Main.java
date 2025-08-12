package org.mcmodule.scwrap;

import java.awt.EventQueue;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;

import javax.sound.midi.*;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HMODULE;

public class Main {

	public static void main(String[] args) throws Throwable {
		int sampleRate = 32000;
		int blockSize = 128;
		String libraryPath = "SCCore.dll";
		String midiA = null, midiB = null, midiOut = null;
		String output = null;
		int map = 4;
		boolean gui = false;

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
				case "--help":
				case "-h":
					System.out.println("Usage:");
					System.out.println("  -r, --rate            <sample rate>   Set sample rate (default: 32000)");
					System.out.println("  -s, --block            <block size>   Set block size (default: 128)");
					System.out.println("  -l, --lib            <library path>   Set SCCore.dll library path (default: SCCore.dll)");
					System.out.println("  -a, --midiA, -midi <port name/file>   Set MIDI input A");
					System.out.println("  -b, --midiB        <port name/file>   Set MIDI input B");
					System.out.println("  -p, --midiOut           <port name>   Set MIDI output");
					System.out.println("  -o, --output            <file name>   Set audio output file");
					System.out.println("  -m, --map                <map type>   Set map type");
					System.out.println("      --gui                             Open gui");
					return;
				case "--gui":
					gui = true;
					break;
				default:
					System.err.println("Unknown option: " + args[i]);
					break;
			}
		}

		SoundCanvas sc = new SoundCanvas(libraryPath, sampleRate, blockSize);
		SCCoreVersion version = SCCoreVersion.identifyVersion(libraryPath);
		HMODULE tgModule = null;
		if (version != null) {
			System.out.printf("SCCore version: %s (%s)\n", version.getInternalVersion(), version.name());
			tgModule = Kernel32.INSTANCE.GetModuleHandle(libraryPath);
		} else {
			System.out.println("Unable to identify version.");
		}
		
		AbstractGui frame = null;
		if (gui) {
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
		System.out.println("MIDI in devices:");
		for (int i = 0; i < midiInDevice.length; i++) {
			Info info = midiInDevice[i];
			System.out.printf("%d. %s\n", i, info);
		}
		Sequencer sequencerA = null, sequencerB = null;
		Receiver receiver = null;
		if (midiA != null)
			sequencerA = openMidiDeviceOrMidiFile(sc, midiInDevice, midiA, 0);
		
		if (midiB != null)
			sequencerB = openMidiDeviceOrMidiFile(sc, midiInDevice, midiB, 1);
		
		if (midiOut != null) {
			if (version != null) {
				Info[] midiOutDevice = getMidiOutDevice();
				System.out.println("MIDI out devices:");
				for (int i = 0; i < midiOutDevice.length; i++) {
					Info info = midiOutDevice[i];
					System.out.printf("%d. %s\n", i, info);
				}
				Info info = findMidiDevice(midiOutDevice, midiOut);
				if (info == null) {
					System.err.printf("No such device: %s\n", midiOut);
					return;
				}
				System.out.printf("Open midi out device: %s\n", info.getName());
				MidiDevice midiDevice;
				try {
					midiDevice = MidiSystem.getMidiDevice(info);
					midiDevice.open();
					receiver = midiDevice.getReceiver();
				} catch (MidiUnavailableException e) {
					System.err.printf("Unable open midi out device: %s\n", info.getName());
					e.printStackTrace();
				}
				
			} else {
				System.out.println("MIDI output require supported SCCore version!");
			}
		}
		
		if (frame != null)
			EventQueue.invokeLater(frame::open);
		
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
		for (;;) {
			sc.process(out);
			if (frame != null)
				frame.process(out);
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
				if (receiver != null) {
					Pointer ptr = new Pointer(Pointer.nativeValue(tgModule.getPointer().getPointer(version.getEventBufferQueueVariable())) + 192 * 2);
					int writerIndex = ptr.getShort(10) & 0xFFFF;
					int length      = ptr.getShort(12) & 0xFFFF;
					if (readerIndex != writerIndex) {
						int result = ptr.getPointer(0).getInt(readerIndex++ * 4);
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

	private static Sequencer openMidiDeviceOrMidiFile(SoundCanvas sc, Info[] midiInDevice, String name, int portNo) {
		if (name.toLowerCase().endsWith(".mid")) {
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
		} else openMidiDevice(sc, midiInDevice, name, portNo);
		return null;
	}

	private static void openMidiDevice(SoundCanvas sc, Info[] midiInDevice, String name, int portNo) {
		Info info = findMidiDevice(midiInDevice, name);
		if (info == null) {
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
