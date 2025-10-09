package org.mcmodule.scwrap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.Kernel32;

public class MultiInstancedSoundCanvas extends SoundCanvas {

	private static final byte[][] RESET_MESSAGES = {
			"\360\103\020\114\000\000\176\000\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\022\000\000\177\000\001\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\101\020\102\022\100\000\177\000\101\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\176\177\011\001\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\176\177\011\002\367".getBytes(StandardCharsets.ISO_8859_1),
			"\360\176\177\011\003\367".getBytes(StandardCharsets.ISO_8859_1),
	};
	
	private final SoundCanvas[] instances;
	private final int instanceCount;
	private int[][][] noteRefCount;
	private int index = 0;

	public MultiInstancedSoundCanvas(File libraryPath, float sampleRate, int bufferSize, int instances) throws IOException {
		super(sampleRate, bufferSize);
		assert instances > 1;
		this.instanceCount = instances;
		this.instances = new SoundCanvas[instances];
		patchTG(libraryPath.getAbsolutePath());
		File[] libraries = duplicateLibrary(libraryPath, instances);
		for (int i = 0; i < instances; i++) {
			this.instances[i] = new SoundCanvas(Native.load(libraries[i].getAbsolutePath(), TG.class), sampleRate, bufferSize);
		}
		if (System.getProperty("os.name", "unknown").toLowerCase().startsWith("win")) {
			Runtime.getRuntime().addShutdownHook(new Thread() { // Unload library from memory
				@Override
				public void run() {
					Kernel32 kernel32 = Kernel32.INSTANCE;
					for (int i = 0; i < instances; i++) {
						kernel32.FreeLibrary(kernel32.GetModuleHandle(libraries[i].getAbsolutePath()));
					}
				}
			});
		}
		this.noteRefCount = new int[instances][32][128];
		reset();
	}

	@Override
	public void process(float[] buffer, int off, int len) {
//		int actives = 0;
//		for (int i = 0; i < this.instanceCount; i++) {
//			for (int j = 0; j < 32; j++) {
//				for (int k = 0; k < 128; k++) {
//					actives += this.noteRefCount[i][j][k];
//				}
//			}
//		}
//		System.out.println(actives);
		flushMidi();
		SoundCanvas[] instances = this.instances;
		for (int i = 0; i < this.instanceCount; i++) {
			instances[i].process(buffer, off, len);
		}
	}

	@Override
	protected void handleLongMessage(int portNo, byte[] msg, int len) {
		SoundCanvas[] instances = this.instances;
		for (int i = 0; i < this.instanceCount; i++) {
			instances[i].handleLongMessage(portNo, msg, len);
		}
		loop:
		for (int i = 0; i < RESET_MESSAGES.length; i++) {
			byte[] resetMessage = RESET_MESSAGES[i];
			if (len == resetMessage.length) {
				for (int j = 0; j < len; j++) {
					if (msg[j] != resetMessage[j]) {
						continue loop;
					}
				}
				reset();
			}
		}
	}

	@Override
	protected void handleShortMessage(int portNo, int msg) {
		SoundCanvas[] instances = this.instances;
		if ((msg & 0x60) != 0x00) { // Other short message
			for (int i = 0, len = this.instanceCount; i < len; i++) {
				instances[i].handleShortMessage(portNo, msg);
			}
			if ((msg & 0xF0) == 0xB0) {
				int channel = msg & 0xF | (portNo << 4);
				int control = (msg >>> 8) & 0xFF;
				if (control == 0x78 || control == 0x7B) {
					reset(channel);
				}
			}
		} else { // Note off, on
			int inst = lookupInstance(portNo, msg);
			if (inst >= 0 && inst < this.instanceCount) {
				instances[inst].handleShortMessage(portNo, msg);
			} else if (inst == -1) {
				for (int i = 0, len = this.instanceCount; i < len; i++) {
					instances[i].handleShortMessage(portNo, msg);
				}
			}
		}
	}
	
	private void reset() {
		int[][][] noteRefCount = this.noteRefCount;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			for (int j = 0; j < 32; j++) {
				Arrays.fill(noteRefCount[i][j], 0);
			}
		}
		this.index = 0;
	}
	
	private void reset(int ch) {
		int[][][] noteRefCount = this.noteRefCount;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			Arrays.fill(noteRefCount[i][ch], 0);
		}
	}

	// TODO: Always use inst 0 when Part EFX enabled or is Rhythm part when instanceCount > 32
	private int lookupInstance(int portNo, int msg) {
		boolean noteOn = (msg & 0x80) != 0x00;
		int channel = msg & 0xF | (portNo << 4);
		int key = (msg >>> 8) & 0xFF;
		int velocity = (msg >>> 16) & 0xFF;
		if (velocity == 0)
			noteOn = false;
		int[][][] noteRefCount = this.noteRefCount;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			int[][] refCnt = noteRefCount[i];
			if (refCnt[channel][key] > 0) {
				if (noteOn)
					refCnt[channel][key]++;
				else
					refCnt[channel][key]--;
				return i;
			}
		}
		assert noteOn;
		if (!noteOn) {
			return -2;
		}
		int index;
		if (this.instanceCount <= 32) {
			index = channel % this.instanceCount;
		} else {
			index = ++this.index % this.instanceCount;
			this.index = index;
		}
		int[][] refCnt = noteRefCount[index];
		if (noteOn)
			refCnt[channel][key]++;
		else
			refCnt[channel][key]--;
		return index;
	}

	@Override
	public void setSampleRate(float sampleRate) {
		this.sampleRate = sampleRate;
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			instances[i].setSampleRate(sampleRate);
		}
	}

	@Override
	public void setBufferSize(int bufferSize) {
		this.bufferSize = bufferSize;
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			instances[i].setBufferSize(bufferSize);
		}
	}

	@Override
	public int getCurrentTotalRunningVoices() {
		int voices = 0;
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			voices += instances[i].getCurrentTotalRunningVoices();
		}
		return voices;
	}

	@Override
	public int activate() {
		int errcode = 0;
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			errcode = instances[i].activate();
			if (errcode != 0) break;
		}
		return errcode;
	}

	@Override
	public int deactivate() {
		int errcode = 0;
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			errcode = instances[i].deactivate();
			if (errcode != 0) break;
		}
		return errcode;
	}

	@Override
	public void changeMap(int map) {
		SoundCanvas[] instances = this.instances;
		for (int i = 0, len = this.instanceCount; i < len; i++) {
			instances[i].changeMap(map);
		}
	}

	public static File[] duplicateLibrary(File libraryPath, int count) throws IOException {
		if (!libraryPath.exists() || !libraryPath.isFile()) {
			throw new IllegalArgumentException(libraryPath.toString());
		}
		
		int checksum = getCRC32(libraryPath);

		Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"));
		
		String name = libraryPath.getName();
		int dotIndex = name.indexOf('.');
		String prefix = dotIndex < 0 ? name : name.substring(0, dotIndex);
		String suffix = dotIndex < 0 ? ""   : name.substring(dotIndex);

		File[] result = new File[count];
		for (int i = 0; i < count; i++) {
			String fileName = String.format("%s_%08x_%02d%s", prefix, checksum, i, suffix);
			Path tempFile = tempDir.resolve(fileName);
			File file = tempFile.toFile();
			result[i] = file;
			try {
				Files.deleteIfExists(tempFile);
			} catch (IOException e) {
			}
			if (file.exists()) continue;

			Files.copy(libraryPath.toPath(), tempFile, StandardCopyOption.REPLACE_EXISTING);
			file.deleteOnExit();
		}
		return result;
	}

	private static int getCRC32(File file) {
		CRC32 crc = new CRC32();
		byte[] buffer = new byte[4096];
		int len;
		try (FileInputStream in = new FileInputStream(file)){
			while ((len = in.read(buffer)) > 0) {
				crc.update(buffer, 0 , len);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (int) crc.getValue();
	}
}
