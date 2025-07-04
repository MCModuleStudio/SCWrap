package org.mcmodule.scwrap;
import static javax.sound.midi.ShortMessage.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.NoSuchElementException;
import java.util.StringJoiner;

import javax.sound.midi.MidiMessage;
import javax.sound.midi.Receiver;

import com.sun.jna.Memory;
import com.sun.jna.Native;

public class SoundCanvas {
	
	protected final ByteRingBuffer[] uartBuffer;
	protected final TG tg;
	protected float sampleRate;
	protected int bufferSize;
	
	private Memory bufferL, bufferR;
	private byte[] sysexBuffer = new byte[4096], singleByte = new byte[1];
	
	public SoundCanvas(String libraryPath, float sampleRate, int bufferSize) {
		this(patchAndLoadLibrary(libraryPath), sampleRate, bufferSize);
	}

	public SoundCanvas(TG tg, float sampleRate, int bufferSize) {
		this.tg = tg;
		this.sampleRate = sampleRate;
		this.bufferSize = bufferSize;
		tg.TG_initialize(0);
		activate();
		ByteRingBuffer[] uartBuffer = this.uartBuffer = new ByteRingBuffer[2];
		for (int i = 0, len = uartBuffer.length; i < len; i++) {
			uartBuffer[i] = new ByteRingBuffer(65536);
		}
	}
	
	public Receiver createReceiver(int port) {
		ByteRingBuffer buffer = this.uartBuffer[port];
		
		return new Receiver() {

			@Override
			public void send(MidiMessage message, long timeStamp) {
				byte[] msg = message.getMessage();
				buffer.writeFully(msg, 0, msg.length);
			}

			@Override
			public void close() {
				
			}
			
		};
	}
	
	public boolean postMidi(int portNo, int data) {
		int len = getDataLen(data & 0xFF) + 1;
		ByteRingBuffer buffer = this.uartBuffer[portNo];
		synchronized (this) {
			for (int i = 0; i < len; i++) {
				if (!buffer.offer((byte) ((data >> (i << 3)) & 0xFF))) {
					notifyBufferFull(portNo);
					return false;
				}
			}
		}
		return true;
	}
	
	public boolean postMidi(int portNo, byte[] data) {
		int len = data.length;
		ByteRingBuffer buffer = this.uartBuffer[portNo];
		synchronized (this) {
			for (int i = 0; i < len; i++) {
				if (!buffer.offer(data[i])) {
					notifyBufferFull(portNo);
					return false;
				}
			}
		}
		return true;
	}

	public boolean postMidi(int portNo, byte data) {
		ByteRingBuffer buffer = this.uartBuffer[portNo];
		boolean success = buffer.offer(data);
		if (!success) {
			notifyBufferFull(portNo);
		}
		return success;
	}
	
	public void process(float[] buffer) {
		this.process(buffer, 0, buffer.length);
	}
	
	public void process(float[] buffer, int off, int len) {
		checkBounds(buffer.length, off, len);
		if ((len & 1) != 0) {
			throw new IllegalArgumentException("buffer length not even");
		}
		flushMidi();
		int bufferSize = this.bufferSize;
		int bytes = bufferSize * Float.BYTES;
		if (this.bufferL == null || this.bufferL.size() < bytes) {
			this.bufferL = new Memory(bytes);
			this.bufferR = new Memory(bytes);
		}
		Memory bufferL = this.bufferL;
		Memory bufferR = this.bufferR;
		TG tg = this.tg;
		do {
			int reaming = Math.min(len >> 1, bufferSize);
			tg.TG_Process(bufferL, bufferR, reaming);
			reaming <<= 1;
			for (int i = 0; i < reaming; i += 2) {
				buffer[off + i + 0] = bufferL.getFloat(i << 1);
				buffer[off + i + 1] = bufferR.getFloat(i << 1);
			}
			off += reaming;
			len -= reaming;
		} while(len > 0);
	}

	public int activate() {
		return tg.TG_activate(this.sampleRate, this.bufferSize);
	}

	public int deactivate() {
		return tg.TG_deactivate();
	}

	public boolean flushMidi() {
		loop:
		for (int i = 0; i < 2; i++) {
			ByteRingBuffer buffer = this.uartBuffer[i];
			if (buffer.isEmpty()) continue;
			try {
				int maxMidiProcess = 200;
				do {
					int statusByte = pollBuffer(buffer);
					if (statusByte == 0xF0) {
						int j = 0;
						byte[] sysexBuffer = this.sysexBuffer;
						sysexBuffer[j++] = (byte) statusByte;
						do {
							statusByte = pollBuffer(buffer);
							sysexBuffer[j++] = (byte) statusByte;
						} while (statusByte != 0xF7);
						handleLongMessage(i, sysexBuffer, j);
					} else {
						int len = getDataLen(statusByte);
						int msg = statusByte;
						for (int j = 1; j <= len; j++)
							msg |= pollBuffer(buffer) << (j << 3);
						handleShortMessage(i, msg);
					}
				} while(!buffer.isEmpty() && maxMidiProcess-- > 0);
				continue loop;
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
			System.out.println("Ilegal midi data deteced at port " + i);
		}
		tg.TG_flushMidi();
		return !(this.uartBuffer[0].isEmpty() || this.uartBuffer[1].isEmpty());
	}
	
	protected void handleLongMessage(int portNo, byte[] msg, int len) {
		if (msg[1] == 0x43 && msg[2] == 0x00 && msg[3] == 0x5D) return; // Fix XG bulk dump cause crash
		TG tg = this.tg;
		int[] packets = PacketEncoder.encodeLongMessage(portNo, msg, 0, len);
		for (int i = 0, l = packets.length; i < l; i++) {
			int packet = packets[i];
			if (packet != 0)
				tg.TG_PMidiIn(packet, 0);
		}
	}
	
	protected void handleShortMessage(int portNo, int msg) {
		int packet = PacketEncoder.encodeShortMessage(portNo, msg);
		if (packet != 0)
			this.tg.TG_PMidiIn(packet, 0);
	}

	private int pollBuffer(ByteRingBuffer buffer) {
		byte[] singleByte = this.singleByte;
		if (buffer.read(singleByte, 0, 1) == 0)
			throw new NoSuchElementException();
		return singleByte[0] & 0xFF;
	}

	public float getSampleRate() {
		return sampleRate;
	}

	public void setSampleRate(float sampleRate) {
		this.sampleRate = sampleRate;
		tg.TG_setSampleRate(sampleRate);
	}

	public int getBufferSize() {
		return bufferSize;
	}

	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
		tg.TG_setMaxBlockSize(bufferSize);
	}
	
	protected void notifyBufferFull(int portNo) {
		
	}

	private int getDataLen(int status) {
		switch (status & 0xF0) {
		case NOTE_OFF: // Note off
		case NOTE_ON: // Note on
		case POLY_PRESSURE: // Polyphonic Aftertouch
		case CONTROL_CHANGE: // Control Change
		case PITCH_BEND: // Pitch blend
			return 2;
		case PROGRAM_CHANGE: // Program Change
		case CHANNEL_PRESSURE: // Channel Aftertouch
			return 1;
		case 0xF0:
			switch (status) {
			case MIDI_TIME_CODE:
			case SONG_SELECT:
				return 1;
			case SONG_POSITION_POINTER:
				return 2;
			default:
				return 0;
			}
		default:
			throw new RuntimeException("Invalid status byte: " + status);
		}
	}
	
	private static void checkBounds(int arrlen, int index, int length) {
		if (index < 0 || index + length > arrlen) throw new ArrayIndexOutOfBoundsException();
	}
	
	protected static String toHex(byte[] data, int len) {
		StringJoiner sj = new StringJoiner(" ");
		for (int i = 0; i < len; i++) {
			int b = data[i] & 0xFF;
			sj.add(hex(b >> 4) + hex(b & 0xF));
		}
		return sj.toString();
	}
	
	private static String hex(int i) {
		char ch = (char) (i >= 10 ? (i - 9) ^ 64 : i ^ 48);
		return new String(new char[] {ch}, 0, 1);
	}
	
	private static TG patchAndLoadLibrary(String libraryPath) {
		try {
			if (new File(libraryPath).exists())
				patchTG(libraryPath);
		} catch (Throwable t) {
			t.printStackTrace();
		}
		return Native.load(libraryPath, TG.class);
	}
	
	public static void patchTG(String libraryPath) throws IOException {
		System.out.println("Try to patch SCCore.dll");
		try (RandomAccessFile file = new RandomAccessFile(libraryPath, "rw")) {
			System.out.println("Searching for `and al, 0Fh`");
			byte[] buffer = new byte[(int) file.length()];
			file.read(buffer);
			ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.BIG_ENDIAN);
			for (int i = 0, len = (int) (file.length() - 4); i < len; i++) {
				int dword = byteBuffer.getInt(i);
				if (dword == 0x240FBA04 || dword == 0x240F8845 || dword == 0x240F8844) {
					System.out.printf("Found opcode at 0x%08x\n", i);
					file.seek(i);
					System.out.println("Replacing with Nop");
					file.writeShort(0x9090);
					System.out.println("Patch completed");
					return;
				}
				if (dword == 0xE00FBA04) {
					i -= 2;
					System.out.printf("Found opcode at 0x%08x\n", i);
					file.seek(i);
					System.out.println("Replacing with Nop");
					file.writeInt(0x90909090);
					System.out.println("Patch completed");
					return;
				}
				if (dword == 0x9090BA04 || dword == 0x90908845 || dword == 0x90908844) {
					if ((byteBuffer.getShort(i - 2) & 0xFFFF) == 0x9090)
						i -= 2;
					System.out.printf("Found patched opcode at 0x%08x\n", i);
					System.out.println("Patch completed");
					return;
				}
			}
			System.out.println("Opcode not found, 2 Parts will not available.");
		}
	}
}
