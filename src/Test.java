
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.MidiDevice.Info;
import javax.sound.midi.MidiMessage;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioFormat.Encoding;

import org.mcmodule.scwrap.PacketEncoder;
import org.mcmodule.scwrap.TG;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.platform.win32.WinDef.HMODULE;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public class Test {

	public static void main(String[] args) throws Throwable {
		int sampleRate = 32000; // Avoid resample
		int blockSize = 128;
		ConcurrentLinkedQueue<Integer> messages = new ConcurrentLinkedQueue<>();
		AudioFormat format = new AudioFormat(Encoding.PCM_SIGNED, sampleRate, 16, 2, 4, sampleRate, true);
		SourceDataLine line = AudioSystem.getSourceDataLine(format);
		String dllPath = "L:\\Works\\SCCore\\SCCore.dll";
		TG tg = Native.load(dllPath, TG.class);
		HMODULE tgModule = Kernel32.INSTANCE.GetModuleHandle(dllPath);
//		System.out.println(Pointer.nativeValue(tgModule.getPointer().getPointer(0x6ba58 + 0x01A0D000)));
		System.out.println(tg.TG_initialize(0));
		System.out.println(tg.TG_activate(sampleRate, blockSize));
		Pointer base = tgModule.getPointer().getPointer(0x6ba58 + 0x01A0D000);
		Memory config = new Memory(Integer.BYTES * 2);
		config.setInt(0, 1);
		config.setInt(4, 1);
		tg.TG_XPsetSystemConfig(config);
		line.open(format, 4096);
		line.start();
		Info[] midiInDevice = getMidiInDevice();
		System.out.println("MIDI in devices:");
		for (int i = 0; i < midiInDevice.length; i++) {
			Info info = midiInDevice[i];
			System.out.printf("%d. %s\n", i, info);
		}
		Info infoA = midiInDevice[4];
		System.out.printf("Open midi device A: %s\n", infoA.getName());
		MidiDevice midiDeviceA = MidiSystem.getMidiDevice(infoA);
		midiDeviceA.open();
		midiDeviceA.getTransmitter().setReceiver(new Receiver() {

			@Override
			public void send(MidiMessage message, long timeStamp) {
				byte[] msg = message.getMessage();
				synchronized (messages) {
					if (msg.length > 3) {
						int[] packets = PacketEncoder.encodeLongMessage(0, msg, 0, msg.length);
						for (int i = 0, len = packets.length; i < len; i++) {
							int packet = packets[i];
							if (packet != 0)
								messages.add(packet);
						}
					} else {
						int i = 0;
						for (int j = 0, len = msg.length; j < len; j++) {
							i |= (msg[j] & 0xFF) << (8 * j);
						}
						int packet = PacketEncoder.encodeShortMessage(0, i);
						if (packet != 0)
							messages.add(packet);
					}
				}
			}

			@Override
			public void close() {
				
			}
			
		});
		
		Info infoB = midiInDevice[7];
		System.out.printf("Open midi device B: %s\n", infoB.getName());
		MidiDevice midiDeviceB = MidiSystem.getMidiDevice(infoB);
		midiDeviceB.open();
		midiDeviceB.getTransmitter().setReceiver(new Receiver() {

			@Override
			public void send(MidiMessage message, long timeStamp) {
				byte[] msg = message.getMessage();
				synchronized (messages) {
					if (msg.length > 3) {
						int[] packets = PacketEncoder.encodeLongMessage(1, msg, 0, msg.length);
						for (int i = 0, len = packets.length; i < len; i++) {
							int packet = packets[i];
							if (packet != 0)
								messages.add(packet);
						}
					} else {
						int i = 0;
						for (int j = 0, len = msg.length; j < len; j++) {
							i |= (msg[j] & 0xFF) << (8 * j);
						}
						int packet = PacketEncoder.encodeShortMessage(1, i);
						if (packet != 0)
							messages.add(packet);
					}
				}
			}

			@Override
			public void close() {
				
			}
			
		});
		
//		FileOutputStream output = new FileOutputStream("Test.pcm");
		float[] out = new float[blockSize << 1];
		Memory l = new Memory(blockSize * Float.BYTES);
		Memory r = new Memory(blockSize * Float.BYTES);
		for (;;) {
			int maxPacketCount = 256;
			synchronized (messages) {
				while (!messages.isEmpty() && maxPacketCount-- > 0)
					tg.TG_PMidiIn(messages.poll(), 0);
			}
			tg.TG_flushMidi();
			tg.TG_Process(l, r, blockSize);
//			getPartLevel(base, 0);
//			System.out.printf("PartLevel: %d\n", getPartLevel(base, 0));
//			System.out.printf("Polyphony: %d\n", tg.TG_XPgetCurTotalRunningVoices());
			for (int i = 0; i < blockSize; i++) {
				out[i * 2 + 0] = l.getFloat(i * Float.BYTES);
				out[i * 2 + 1] = r.getFloat(i * Float.BYTES);
			}
			byte[] byteArray = toByteArray(out);
			line.write(byteArray, 0, byteArray.length);
//			output.write(byteArray);
		}
	}

	private static int getPartLevel(Pointer base, int part) {
		Pointer pointer = new Pointer(Pointer.nativeValue(base) + 1160 * part);
		
		System.out.println(pointer.dump(620, 8));
		
		Pointer v3 = pointer.getPointer(624);
		if (v3 == null) return 0;
		System.out.println(pointer.dump(0, 8));
		v3.getLong(0);
		for (Pointer i = v3.getPointer(0); ; ) {
			if (i == null) {
				v3 = v3.getPointer(4 * 8);
			}
			break;
		}
		for (Pointer i = v3.getPointer(0); ; i = i.getPointer(264)) {
			long v6 = (i.getInt(172) >> 2) * (i.getInt(156) >> 2);
			System.out.println(v6);
			if (v6 >= 0x3FFFFFFF) break;
		}
		return 0;
	}

	private static byte[] toByteArray(float[] in) {
		byte[] out = new byte[in.length << 1];
		for (int i = 0, j = 0, len = in.length; i < len; i++) {
			int value = (int) ((in[i]) * 32768f);
			if (value >  32767)
				value =  32767;
			if (value < -32768)
				value = -32768;
//			value = -32768;
//			int value = Float.floatToRawIntBits(in[i]);
//			out[j++] = (byte) (value >> 24);
//			out[j++] = (byte) (value >> 16);
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
