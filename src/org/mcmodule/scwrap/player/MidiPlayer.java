package org.mcmodule.scwrap.player;

import static javax.sound.midi.ShortMessage.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;


import org.mcmodule.scwrap.SoundCanvas;

public class MidiPlayer implements AutoCloseable {

	private final File file;
	private final int tracks, resolution;
	private final long[] startPos;
	private final long totalTicks;
	private final SoundCanvas soundCanvas;
	private long currentTick;
	private MidiInputStream[] streams;
	private ChannelStatus[] statuses;
	private boolean started = false;
	private long lastElapsedMicros = 0L;
	private long currentTempo = 500000;
	private double tickRemainder = 0d;
	private int portOffset;

	public MidiPlayer(File file, SoundCanvas sc, int portOffset) throws IOException {
		this.file = file;
		this.soundCanvas = sc;
		this.portOffset = portOffset;
		RandomAccessFile raf = new RandomAccessFile(file, "r");
		try (MidiInputStream stream = new MidiInputStream(raf)){
			if (stream.readInt() != 0x4D546864) throw new IOException("Not a midi file");
			int headerSize = stream.readInt();
			int midiType = stream.readShort();
			if (midiType < 0 || midiType > 1)
				throw new IOException(String.format("Midi type %d not supported yet!", midiType));
			this.tracks = stream.readShort();
			int times = stream.readUnsignedShort();
			if ((times & 0x8000) != 0) throw new IOException("SMPTE time not supported yet!");
			times &= 0x7FFF;
			if (headerSize != 6) {
				System.out.println("WARNING: Extra header data was found on this midi!");
				stream.readFully(new byte[headerSize - 6]);
			}
			this.resolution = times;
			long[] start = new long[tracks];
			long totalTicks = 0;
			long offset = 8 + headerSize;
			for (int id = 0; id < tracks; id++) {
				start[id] = offset;
				if (stream.readInt() != 0x4D54726B) throw new IOException("Not a midi track at " + offset);
				offset += 8;
				long length = stream.readInt() & 0xFFFFFFFFL;
				offset += length;
				int lastStatus = 0x00;
				long totalTick = 0;
				loop:
				while (true) {
					long time = stream.readVarLong();
					totalTick += time;
					int status = stream.readUnsignedByte();
					int data;
					if (!isVaildStatus(status)) {
						data = status;
						status = lastStatus;
					} else {
						lastStatus = status;
						data = stream.readUnsignedByte();
					}
					switch (status) {
						case 0xF0: // System exclusive message
							byte[] sysex = new byte[stream.readVarInt(data)];
							stream.readFully(sysex);
							break;
						case 0xFF: // Metadata
							int len = stream.readVarInt();
							byte[] dta = new byte[len];
							stream.readFully(dta);
							if (data == 0x2F) break loop;
							break;
						default: // Short message
							if (getDataLen(status) == 2)
								stream.readUnsignedByte();
							break;
					}
				}
				if (totalTick > totalTicks) totalTicks = totalTick;
			}
			this.totalTicks = totalTicks;
			this.startPos = start;
		}
	}

	public void start() throws IOException {
		if (this.started)
			return;
		int tracks = this.tracks;
		this.streams = new MidiInputStream[tracks];
		this.statuses = new ChannelStatus[tracks];
		for (int i = 0; i < tracks; i++) {
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(this.startPos[i] + 8);
			this.streams[i] = new MidiInputStream(raf);
			this.statuses[i] = new ChannelStatus(i);
			readEvent(this.streams[i], this.statuses[i]);
		}
		this.currentTick = 0;
		this.lastElapsedMicros = 0;
		this.started = true;
		this.currentTempo = 500000;
	}

	public void loop(long elapsedMicros) throws IOException {
		if (!this.started)
			return;
		long deltaMicros = elapsedMicros - this.lastElapsedMicros;
		if (deltaMicros <= 0)
			return;
		this.lastElapsedMicros = elapsedMicros;

		double microsPerTick = this.currentTempo / (double) this.resolution;
		double ticksToAdvance = deltaMicros / microsPerTick;

		this.tickRemainder += ticksToAdvance;
		while (tickRemainder >= 1.0 && this.currentTick <= this.totalTicks) {
			processTick();
			this.tickRemainder -= 1.0;
			this.currentTick++;
		}
	}

	private void processTick() throws IOException {
		SoundCanvas sc = this.soundCanvas;
		for (int i = 0; i < this.tracks; i++) {
			ChannelStatus ch = this.statuses[i];
			MidiInputStream in = this.streams[i];
			while (!ch.completed && ch.next == 0) {
				byte[] data = ch.nextEventData;
				if (data[0] == (byte) 0xFF) {
					switch (data[1] & 0xFF) {
					case 0x51: // tempo change
						long tempo = 0;
						for (int j = 2; j < data.length; j++)
							tempo = (tempo << 8) | (data[j] & 0xFF);
						this.currentTempo = tempo;
						break;
					case 0x21:
						if (data.length > 2) {
							ch.portNo = data[2] & 0xFF;
						}
						break;
					}
				} else {
					int portNo = ch.portNo;
					if (portNo < 2) {
						sc.postMidi((portNo + this.portOffset) & 1, data);
					}
				}
				readEvent(in, ch);
			}
			if (!ch.completed)
				ch.next--;
		}
	}

	public void close() throws IOException {
		if (!this.started)
			return;
		for (int i = 0; i < this.tracks; i++) {
			this.streams[i].close();
		}
		resetSound(this.soundCanvas);
		this.started = false;
	}

	private boolean readEvent(MidiInputStream in, ChannelStatus ch) throws IOException {
		if (ch.completed) return false;
		ch.next = in.readVarLong();
		int status = in.readUnsignedByte();
		int data;
		if (!isVaildStatus(status)) {
			data = status;
			status = ch.lastStatus;
		} else {
			ch.lastStatus = status;
			data = in.readUnsignedByte();
		}
		byte[] dat;
		switch (status) {
			case 0xF0: { // System exclusive message
				dat = new byte[in.readVarInt(data) + 1];
				dat[0] = (byte) 0xF0;
				in.readFully(dat, 1, dat.length - 1);
				break;
			}
			case 0xFF: { // Metadata
				int len = in.readVarInt();
				dat = new byte[len + 2];
				dat[0] = (byte) 0xFF;
				dat[1] = (byte) data;
				in.readFully(dat, 2, dat.length - 2);
				if (data == 0x2F) {
					ch.nextEventData = dat;
					ch.completed = true;
					return false;
				}
				break;
			}
			default: { // Short message
				dat = new byte[1 + getDataLen(status)];
				dat[0] = (byte) status;
				dat[1] = (byte) data;
				if (dat.length == 3) dat[2] = in.readByte();
				break;
			}
		}
		ch.nextEventData = dat;
		return true;
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
			default:
				throw new RuntimeException("Invalid status byte: " + status);
		}
	}

	private static boolean isVaildStatus(int status) {
		return (status & 0x80) != 0;
	}

	private void resetSound(SoundCanvas sc) {
		byte[] data = new byte[]{(byte) CONTROL_CHANGE, 0, 0};
		for (int i = 0; i < 16; i++) {
			data[0] = (byte) (CONTROL_CHANGE | i);
			data[1] = 0x78;
			sc.postMidi(0, data);
			sc.postMidi(1, data);
			data[1] = 0x7B;
			sc.postMidi(0, data);
			sc.postMidi(1, data);
		}
	}

	public long getCurrentTick() {
		return this.currentTick;
	}
	
	public long getTotalTicks() {
		return this.totalTicks;
	}
	
	public boolean isPlaying() {
		return this.currentTick < this.totalTicks;
	}
	
	static class ChannelStatus {
		public final int id;
		public long next;
		public byte[] nextEventData;
		public int lastStatus;
		public boolean completed;
		public int portNo = 0;

		public ChannelStatus(int i) {
			this.id = i;
		}
	}
}