package org.mcmodule.scwrap;

import java.util.ArrayList;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class Main {

	public static void main(String[] args) throws LineUnavailableException {
		int sampleRate = 32000;
		int blockSize = 128;
		String libraryPath = "SCCore.dll";
		String midiA = null, midiB = null;

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
				case "--help":
				case "-h":
					System.out.println("Usage:");
					System.out.println("  -r, --rate      <sample rate>  Set sample rate (default: 32000)");
					System.out.println("  -s, --block      <block size>  Set block size (default: 128)");
					System.out.println("  -l, --lib      <library path>  Set native library path (default: SCCore.dll)");
					System.out.println("  -a, --midiA, -midi <port name> Set MIDI input A");
					System.out.println("  -b, --midiB        <port name> Set MIDI input B");
					return;
				default:
					System.err.println("Unknown option: " + args[i]);
					break;
			}
		}

		AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, sampleRate, 16, 2, 4, sampleRate, true);
		SourceDataLine line = AudioSystem.getSourceDataLine(format);
		SoundCanvas sc = new SoundCanvas(libraryPath, sampleRate, blockSize);
		line.open(format, 4096);
		line.start();
		Info[] midiInDevice = getMidiInDevice();
		System.out.println("MIDI in devices:");
		for (int i = 0; i < midiInDevice.length; i++) {
			Info info = midiInDevice[i];
			System.out.printf("%d. %s\n", i, info);
		}
		if (midiA != null)
			openMidiDevice(sc, midiInDevice, midiA, 0);
		
		if (midiB != null)
			openMidiDevice(sc, midiInDevice, midiB, 1);
		
		float[] out = new float[blockSize << 1];
		for (;;) {
			sc.process(out);
			byte[] byteArray = toByteArray(out);
			line.write(byteArray, 0, byteArray.length);
		}
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

	private static Info findMidiDevice(Info[] midiInDevice, String name) {
		for (int i = 0, len = midiInDevice.length; i < len; i++) {
			Info info = midiInDevice[i];
			if (info.getName().equals(name))
				return info;
		}
		return null;
	}

	private static byte[] toByteArray(float[] in) {
		byte[] out = new byte[in.length << 1];
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

}
