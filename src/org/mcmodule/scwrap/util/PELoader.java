package org.mcmodule.scwrap.util;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;

import org.mcmodule.scwrap.TG;

import com.sun.jna.Function;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.BaseTSD.SIZE_T;
import com.sun.jna.platform.win32.WinDef.HMODULE;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.win32.W32APIOptions;

public class PELoader {
	
	protected final byte[] data;
	
	protected final Section[] sections;

	protected final int machine;
	protected final int numberOfSections;
	protected final int timeDateStamp;
	protected final int sizeOfOptionalHeader;
	protected final int characteristics;

	protected final int magic;
	protected final int sizeOfCode;
	protected final int sizeOfInitializedData;
	protected final int sizeOfUninitializedData;
	protected final int addressOfEntryPoint;
	protected final int baseOfCode;

	protected long imageBase;

	protected final int sizeOfImage;
	protected final int sizeOfHeaders;

	protected final int checksum;
	protected final int subsystem;
	protected final int dllCharacteristics;

	protected final long sizeOfStackReserve;
	protected final long sizeOfStackCommit;
	protected final long sizeOfHeapReserve;
	protected final long sizeOfHeapCommit;

	protected final int numberOfRvaAndSizes;

	protected final int[] sectionAddress;
	protected final int[] sectionSize;
	
	protected boolean importTableFixed = false;

	public PELoader(File file) throws IOException {
		this(readFile(file));
	}

	public PELoader(byte[] data) {
		ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
		int length = data.length;
		if (length < 64 || buf.getShort() != 0x5A4D)
			throw new Error("DOS Header not found");
		int e_lfanew = buf.getInt(60);
		if (e_lfanew < 0 || e_lfanew + 24 >= length || buf.getInt(e_lfanew) != 0x4550)
			throw new Error("PE Header not found");
		buf.limit(e_lfanew + 24).position(e_lfanew + 4);
		ByteBuffer peHeader = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
		int machine = peHeader.getShort(0) & 0xFFFF;
		if (machine != 0x014C && machine != 0x8664)
			throw new Error("Machine type not supported");
		int numberOfSections = peHeader.getShort(2);
		int timeDateStamp = peHeader.getInt(4);
		int sizeOfOptionalHeader = peHeader.getShort(16) & 0xFFFF;
		int characteristics = peHeader.getShort(18) & 0xFFFF;
		if (sizeOfOptionalHeader <= 0 || e_lfanew + sizeOfOptionalHeader + 24 >= length || buf.getInt(e_lfanew) != 0x4550)
			throw new Error("Optional Header not found");
		buf.limit(e_lfanew + sizeOfOptionalHeader + 24).position(e_lfanew + 24);
		ByteBuffer optionalHeader = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
		int magic = optionalHeader.getShort() & 0xFFFF;
		if (!((machine == 0x8664 && magic == 0x20B) || (machine == 0x014C && magic == 0x10B)))
			throw new Error("Optional Header not supported");
		optionalHeader.getShort();
		int sizeOfCode = optionalHeader.getInt();
		int sizeOfInitializedData = optionalHeader.getInt();
		int sizeOfUninitializedData = optionalHeader.getInt();
		int addressOfEntryPoint = optionalHeader.getInt();
		int baseOfCode = optionalHeader.getInt();
		if (magic == 0x10B)
			optionalHeader.getInt();
		long imageBase = 0L;
		if (magic == 0x10B)
			imageBase = optionalHeader.getInt() & 0xFFFFFFFF;
		else if (magic == 0x20B)
			imageBase = optionalHeader.getLong();
		else assert false;
		optionalHeader.getInt();
		optionalHeader.getInt();
		optionalHeader.getInt();
		optionalHeader.getInt();
		optionalHeader.getInt();
		optionalHeader.getInt();
		int sizeOfImage = optionalHeader.getInt();
		int sizeOfHeaders = optionalHeader.getInt();
		int checksum = optionalHeader.getInt();
		int subsystem = optionalHeader.getShort() & 0xFFFF;
		int dllCharacteristics = optionalHeader.getShort() & 0xFFFF;
		long sizeOfStackReserve = 0, sizeOfStackCommit = 0, sizeOfHeapReserve = 0, sizeOfHeapCommit = 0;
		if (magic == 0x10B) {
			sizeOfStackReserve = optionalHeader.getInt() & 0xFFFFFFFF;
			sizeOfStackCommit = optionalHeader.getInt() & 0xFFFFFFFF;
			sizeOfHeapReserve = optionalHeader.getInt() & 0xFFFFFFFF;
			sizeOfHeapCommit = optionalHeader.getInt() & 0xFFFFFFFF;
		} else if (magic == 0x20B) {
			sizeOfStackReserve = optionalHeader.getLong();
			sizeOfStackCommit = optionalHeader.getLong();
			sizeOfHeapReserve = optionalHeader.getLong();
			sizeOfHeapCommit = optionalHeader.getLong();
		} else assert false;
		optionalHeader.getInt();
		int numberOfRvaAndSizes = optionalHeader.getInt();
		int[] sectionAddress = new int[16];
		int[] sectionSize = new int[16];
		
		for (int i = 0; optionalHeader.remaining() >= 8 && i < 16; i++) {
			sectionAddress[i] = optionalHeader.getInt();
			sectionSize[i] = optionalHeader.getInt();
		}
		assert !optionalHeader.hasRemaining();
		byte[] imageData = new byte[sizeOfImage];
		Section[] sections = new Section[numberOfSections];
		
		System.arraycopy(data, 0, imageData, 0, sizeOfHeaders);
		
		for (int i = 0; i < numberOfSections; i++) {
			buf.limit(e_lfanew + sizeOfOptionalHeader + i * 40 + 64).position(e_lfanew + sizeOfOptionalHeader + i * 40 + 24);
			ByteBuffer section = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
			byte[] nameBytes = new byte[8];
			section.get(nameBytes, 0, 8);
			String name = new String(nameBytes, StandardCharsets.ISO_8859_1);
			int indexOf = name.indexOf('\0');
			if (indexOf >= 0)
				name = name.substring(0, indexOf);
			int virtualSize = section.getInt(8);
			int virtualAddress = section.getInt(12);
			int sizeOfRawData = section.getInt(16);
			int pointerToRawData = section.getInt(20);
			sections[i] = new Section(name, virtualSize, virtualAddress, sizeOfRawData, pointerToRawData, section.getInt(36));
			System.arraycopy(data, pointerToRawData, imageData, virtualAddress, Math.min(virtualSize, sizeOfRawData));
		}
		
		this.data = imageData;
		this.sections = sections;
		this.machine = machine;
		this.numberOfSections = numberOfSections;
		this.timeDateStamp = timeDateStamp;
		this.sizeOfOptionalHeader = sizeOfOptionalHeader;
		this.characteristics = characteristics;

		this.magic = magic;
		this.sizeOfCode = sizeOfCode;
		this.sizeOfInitializedData = sizeOfInitializedData;
		this.sizeOfUninitializedData = sizeOfUninitializedData;
		this.addressOfEntryPoint = addressOfEntryPoint;
		this.baseOfCode = baseOfCode;

		this.imageBase = imageBase;

		this.sizeOfImage = sizeOfImage;
		this.sizeOfHeaders = sizeOfHeaders;

		this.checksum = checksum;
		this.subsystem = subsystem;
		this.dllCharacteristics = dllCharacteristics;

		this.sizeOfStackReserve = sizeOfStackReserve;
		this.sizeOfStackCommit = sizeOfStackCommit;
		this.sizeOfHeapReserve = sizeOfHeapReserve;
		this.sizeOfHeapCommit = sizeOfHeapCommit;

		this.numberOfRvaAndSizes = numberOfRvaAndSizes;

		this.sectionAddress = sectionAddress;
		this.sectionSize = sectionSize;
	}
	
	public void fixImport() {
		if (this.importTableFixed)
			return;
		int sectionAddress = this.sectionAddress[1];
		int sectionSize = this.sectionSize[1];
		if (sectionAddress == 0 || sectionSize == 0)
			return;
		ByteBuffer section = ByteBuffer.wrap(this.data, sectionAddress, sectionSize).slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
		section.mark();
		
		HashSet<String> requiredModules = new HashSet<>();
		
		while (section.remaining() >= 20) {
			int iltAddr = section.getInt();
			int ts = section.getInt();
			int fwc = section.getInt();
			int nameAddr = section.getInt();
			int iatAddr = section.getInt();
			if (iltAddr == 0 && ts == 0 && fwc == 0 && nameAddr == 0 && iatAddr == 0) {
				break;
			}

			if (nameAddr == 0 || iatAddr == 0) {
				throw new IllegalStateException("Invalid import descriptor");
			}
			requiredModules.add(getCString(this.data, this.data.length, nameAddr).toLowerCase());
		}
		
		section.reset();
		
		ByteBuffer buf = ByteBuffer.wrap(this.data).order(ByteOrder.LITTLE_ENDIAN);
		int magic = this.magic;
		
		while (section.remaining() >= 20) {
			int iltAddr = section.getInt();
			int ts = section.getInt();
			int fwc = section.getInt();
			int nameAddr = section.getInt();
			int iatAddr = section.getInt();
			if (iltAddr == 0 && ts == 0 && fwc == 0 && nameAddr == 0 && iatAddr == 0) {
				break;
			}

			if (nameAddr == 0 || iatAddr == 0) {
				throw new IllegalStateException("Invalid import descriptor");
			}

			String moduleName = getCString(this.data, this.data.length, nameAddr);
			HMODULE library = Kernel32.INSTANCE.LoadLibraryEx(moduleName, null, 0);
			if (library == null) {
				throw new Error("Unable to load library: " + moduleName + " Error: " + Kernel32.INSTANCE.GetLastError());
			}
			HMODULE hmodule = new HMODULE();
			hmodule.setPointer(library.getPointer());
			
			int thunkAddr = iltAddr != 0 ? iltAddr : iatAddr;

			try {
				while (true) {

					long thunk = 0;
					long mask = 0;
					if (magic == 0x10B) {
						thunk = buf.getInt(thunkAddr) & 0xFFFFFFFF;
						mask = 0x80000000L;
					} else if (magic == 0x20B) {
						thunk = buf.getLong(thunkAddr);
						mask = 0x8000000000000000L;
					} else assert false;

					if (thunk == 0)
						break;

					Pointer procAddr;

					if ((thunk & mask) != 0) {
						int ordinal = (int) (thunk & 0xFFFF);
						
						procAddr = Kernel32.INSTANCE.GetProcAddress(hmodule, ordinal);

					} else {
						int hintNameRva = (int) thunk;

						String funcName = getCString(this.data, this.data.length, hintNameRva + 2);

						procAddr = Kernel32.INSTANCE.GetProcAddress(hmodule, funcName);
					}
					
					if (procAddr == null)
						throw new Error("GetProcAddress failed");

					long finalAddr = Pointer.nativeValue(procAddr);
					

					if (magic == 0x10B) {
						buf.putInt(iatAddr, (int) finalAddr);
						thunkAddr += 4;
						iatAddr += 4;
					} else if (magic == 0x20B) {
						buf.putLong(iatAddr, finalAddr);
						thunkAddr += 8;
						iatAddr += 8;
					} else assert false;
					
				}
			} finally {
//				Kernel32.INSTANCE.FreeLibrary(hmodule);
			}
		}
		this.importTableFixed = true;
	}
	
	public void relocate(long newAddress) {
		int sectionAddress = this.sectionAddress[5];
		int sectionSize = this.sectionSize[5];
		if (sectionAddress == 0 || sectionSize == 0)
			if ((this.dllCharacteristics & 0x2) == 0)
				throw new Error("PE file is not relocatable");
			else {
				this.imageBase = newAddress;
				return;
			}
		ByteBuffer section = ByteBuffer.wrap(this.data, sectionAddress, sectionSize).slice().asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
		ByteBuffer buf = ByteBuffer.wrap(this.data).order(ByteOrder.LITTLE_ENDIAN);
		long offset = newAddress - this.imageBase;
		this.imageBase = newAddress;
		
		if (offset == 0)
			return;

		int magic = this.magic;

		while (section.remaining() >= 8) {
			int virtualAddress = section.getInt();
			int sizeOfBlock = section.getInt();

			int entryCount = (sizeOfBlock - 8) / 2;
			for (int i = 0; i < entryCount; i++) {
				short typeOffset = section.getShort();
				int type = (typeOffset >> 12) & 0xF;
				int rva = typeOffset & 0x0FFF;

				if (type == 0)
					continue;

				long absAddress = virtualAddress + rva;
				if (magic == 0x10B) {
					int orig = buf.getInt((int) absAddress);
					int relocated = orig + (int) offset;
					buf.putInt((int) absAddress, relocated);
				} else if (magic == 0x20B) {
					long orig = buf.getLong((int) absAddress);
					long relocated = orig + offset;
					buf.putLong((int) absAddress, relocated);
				} else assert false;
			}
		}
	}
	
	public boolean is64Bit() {
		return this.machine == 0x8664;
	}
	
	public long getImageBase() {
		return imageBase;
	}

	public ExportEntry getExportEntry(String targetName) {

		int exportRva = this.sectionAddress[0];
		int exportSize = this.sectionSize[0];

		if (exportRva == 0 || exportSize == 0)
			return null;

		ByteBuffer buf = ByteBuffer.wrap(this.data).order(ByteOrder.LITTLE_ENDIAN);

		int ordinalBase = buf.getInt(exportRva + 16);
//		int numberOfFunctions = buf.getInt(exportRva + 20);
		int numberOfNames = buf.getInt(exportRva + 24);

		int addressOfFunctions = buf.getInt(exportRva + 28);
		int addressOfNames = buf.getInt(exportRva + 32);
		int addressOfNameOrdinals = buf.getInt(exportRva + 36);

		for (int i = 0; i < numberOfNames; i++) {

			int nameRva = buf.getInt(addressOfNames + i * 4);
			String name = getCString(this.data, this.data.length, nameRva);

			if (!name.equals(targetName))
				continue;

			int ordinalIndex = buf.getShort(addressOfNameOrdinals + i * 2) & 0xFFFF;

			int funcRva = buf.getInt(addressOfFunctions + ordinalIndex * 4);

			int ordinal = ordinalBase + ordinalIndex;

			return new ExportEntry(name, ordinal, funcRva);
		}

		return null;
	}

	public ExportEntry getExportEntry(int ordinal) {

		int exportRva = this.sectionAddress[0];
		int exportSize = this.sectionSize[0];

		if (exportRva == 0 || exportSize == 0)
			return null;

		ByteBuffer buf = ByteBuffer.wrap(this.data).order(ByteOrder.LITTLE_ENDIAN);

		int ordinalBase = buf.getInt(exportRva + 16);
		int numberOfFunctions = buf.getInt(exportRva + 20);

		int index = ordinal - ordinalBase;

		if (index < 0 || index >= numberOfFunctions)
			return null;

		int addressOfFunctions = buf.getInt(exportRva + 28);

		int rva = buf.getInt(addressOfFunctions + index * 4);

		return new ExportEntry(null, ordinal, rva);
	}
	
	public java.util.List<ExportEntry> getAllExportedEntry() {
		int exportRva = this.sectionAddress[0];
		int exportSize = this.sectionSize[0];

		if (exportRva == 0 || exportSize == 0)
			return java.util.Collections.emptyList();

		ByteBuffer buf = ByteBuffer.wrap(this.data).order(ByteOrder.LITTLE_ENDIAN);

		int ordinalBase = buf.getInt(exportRva + 16);
		int numberOfFunctions = buf.getInt(exportRva + 20);
		int numberOfNames = buf.getInt(exportRva + 24);

		int addressOfFunctions = buf.getInt(exportRva + 28);
		int addressOfNames = buf.getInt(exportRva + 32);
		int addressOfNameOrdinals = buf.getInt(exportRva + 36);

		String[] names = new String[numberOfFunctions];

		for (int i = 0; i < numberOfNames; i++) {
			int nameRva = buf.getInt(addressOfNames + i * 4);
			String name = getCString(this.data, this.data.length, nameRva);

			int ordinalIndex = buf.getShort(addressOfNameOrdinals + i * 2) & 0xFFFF;
			if (ordinalIndex >= 0 && ordinalIndex < numberOfFunctions)
				names[ordinalIndex] = name;
		}

		java.util.ArrayList<ExportEntry> result = new java.util.ArrayList<>();

		for (int i = 0; i < numberOfFunctions; i++) {
			int funcRva = buf.getInt(addressOfFunctions + i * 4);
			if (funcRva == 0)
				continue;

			int ordinal = ordinalBase + i;
			result.add(new ExportEntry(names[i], ordinal, funcRva));
		}

		return result;
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Library> T load(Class<T> libraryClass) {
		Method[] methods = libraryClass.getMethods();
		HashMap<String, ExportEntry> exportEntries = new HashMap<>();
		for (int i = 0, len = methods.length; i < len; i++) {
			String name = methods[i].getName();
			ExportEntry entry = getExportEntry(name);
			if (entry == null)
				throw new Error("Entry '" + name + "' not found");
			exportEntries.put(name, entry);
		}
		HANDLE process = Kernel32.INSTANCE.GetCurrentProcess();
		
		Pointer memory = Kernel32.INSTANCE.VirtualAllocEx(process, new Pointer(this.imageBase), new SIZE_T(this.sizeOfImage), 0x1000 | 0x2000, 0x40);
		if (memory == null) {
			memory = Kernel32.INSTANCE.VirtualAllocEx(process, memory, new SIZE_T(this.sizeOfImage), 0x1000 | 0x2000, 0x40);
		}
		if (memory == null)
			throw new Error("VirtualAllocEx failed " + Kernel32.INSTANCE.GetLastError());
		relocate(Pointer.nativeValue(memory));
		fixImport();
		memory.write(0L, this.data, 0, this.sizeOfImage);
		registerExceptionTable();
		Function.getFunction(new Pointer(Pointer.nativeValue(memory) + this.addressOfEntryPoint)).invoke(new Object[] {memory, 1, null});
		class PELoaderInvocationHandler implements InvocationHandler {
			
			HashMap<String, Function> entries = new HashMap<>();
			
			public PELoaderInvocationHandler(Pointer memory, HashMap<String, ExportEntry> exportEntries) {
				HashMap<String, Function> entries = this.entries;
				for (Iterator<Entry<String, ExportEntry>> iterator = exportEntries.entrySet().iterator(); iterator.hasNext();) {
					Entry<String, ExportEntry> entry = iterator.next();
					entries.put(entry.getKey(), Function.getFunction(new Pointer(Pointer.nativeValue(memory) + entry.getValue().address)));
				}
			}

			@Override
			public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//				System.out.printf("method = %s args = %s\n", method, Arrays.toString(args));
				String name = method.getName();
				if ("toString".equals(name) && args == null) {
					return proxy.getClass().getName() + "@" + Integer.toHexString(hashCode());
				}
				if ("hashCode".equals(name) && args == null) {
					return System.identityHashCode(proxy);
				}
				return this.entries.get(name).invoke(method.getReturnType(), args == null ? new Object[0] : args);
			}
			
		}
		try {
			return (T) Proxy.getProxyClass(PELoader.class.getClassLoader(), TG.class).getDeclaredConstructor(InvocationHandler.class).newInstance(new PELoaderInvocationHandler(memory, exportEntries));
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			throw new Error(e);
		}
	}
	
	
	private void registerExceptionTable() {
		int sectionAddress = this.sectionAddress[3];
		int sectionSize = this.sectionSize[3];
		if (sectionAddress == 0L || sectionSize == 0)
			return;
		Kernel32.INSTANCE.RtlAddFunctionTable(new Pointer(this.imageBase + sectionAddress), sectionSize / 12, this.imageBase);
	}

	protected static String getCString(byte[] data, int length, int offset) {
		int len = 0;
		while (offset + len < length && data[offset + len] != '\0')
			len++;
		return new String(data, offset, len, StandardCharsets.ISO_8859_1);
	}
	
	private static byte[] readFile(File file) throws IOException {
		if (file.length() >= Integer.MAX_VALUE)
			throw new Error("File too big");
		byte[] data = new byte[(int) file.length()];
		try (FileInputStream in = new FileInputStream(file)) {
			new DataInputStream(in).readFully(data);
		}
		return data;
	}
	
	protected class Section {
		public final String name;
		public final int virtualSize;
		public final int virtualAddress;
		public final int sizeOfRawData;
		public final int pointerToRawData;
		public final int characteristics;

		public Section(String name, int virtualSize, int virtualAddress, int sizeOfRawData, int pointerToRawData, int characteristics) {
			this.name = name;
			this.virtualSize = virtualSize;
			this.virtualAddress = virtualAddress;
			this.sizeOfRawData = sizeOfRawData;
			this.pointerToRawData = pointerToRawData;
			this.characteristics = characteristics;
		}
	}
	
	public static class ExportEntry {
		public final String name;
		public final int ordinal;
		public final long address;

		public ExportEntry(String name, int ordinal, long address) {
			this.name = name;
			this.ordinal = ordinal;
			this.address = address;
		}

		@Override
		public String toString() {
			return "ExportEntry [name=" + name + ", ordinal=" + ordinal + ", address=0x" + Long.toHexString(address) + "]";
		}
	}
	
	// WHY??? ORACLE???
	static interface Kernel32 extends com.sun.jna.platform.win32.Kernel32 {
		Kernel32 INSTANCE = Native.load("kernel32.dll", Kernel32.class, W32APIOptions.ASCII_OPTIONS);
		HANDLE LoadLibrary(String library);
		
		Pointer GetProcAddress(HMODULE hModule, String lpProcName);
		
		boolean RtlAddFunctionTable(Pointer table, int entryCount, long baseAddress);
	}
	
	public static void main(String ... args) throws Throwable {
		PELoader loader = new PELoader(new File("SCCore.dll"));
		TG module1 = loader.load(TG.class);
		TG module2 = loader.load(TG.class);
//		try (FileOutputStream out = new FileOutputStream("dump.bin")) {
//			out.write(loader.data);
//		}
		
		module1.TG_initialize(0);
		module2.TG_initialize(0);
//		for (;;) {
//			System.out.println("Test");
//			Thread.sleep(1000L);
//		}
	}
}
