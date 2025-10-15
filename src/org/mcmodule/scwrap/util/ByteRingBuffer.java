package org.mcmodule.scwrap.util;

public class ByteRingBuffer {
	
	private final int capacity, mask;
	private final byte[] buffer;
	
	private int readerIndex, writerIndex;

	public ByteRingBuffer(int capacity) {
		if ((capacity & (capacity - 1)) != 0) {
			throw new IllegalArgumentException("capacity is not power of 2");
		}
		this.capacity = capacity;
		this.mask = capacity - 1;
		this.buffer = new byte[capacity];
		this.readerIndex = this.writerIndex = 0;
	}
	
	public int readables() {
		int readables = this.writerIndex - this.readerIndex;
		if (readables < 0)
			readables += this.capacity;
		return readables;
	}
	
	public int read(byte[] data, int index, int length) {
		checkBounds(data.length, index, length);
		synchronized (this) {
			if (this.writerIndex > this.readerIndex) {
				int len = Math.min(this.writerIndex - this.readerIndex, length);
				System.arraycopy(this.buffer, this.readerIndex, data, index, len);
				this.readerIndex += len;
				return len;
			} else if (this.writerIndex < this.readerIndex) {
				int len = Math.min(this.capacity - this.readerIndex, length);
				System.arraycopy(this.buffer, this.readerIndex, data, index, len);
				this.readerIndex += len;
				this.readerIndex &= this.mask;
				return len;
			} else return 0;
		}
	}
	
	public int write(byte[] data, int index, int length) {
		checkBounds(data.length, index, length);
		synchronized (this) {
			if (this.writerIndex >= this.readerIndex) {
				int len = Math.min(this.capacity - this.writerIndex - 1, length);
				if (len == 0) {
					this.buffer[this.writerIndex] = data[index];
					if (this.readerIndex == 0) {
						this.readerIndex = 1;
					}
					this.writerIndex = 0;
					return 1;
				}
				System.arraycopy(data, index, this.buffer, this.writerIndex, len);
				this.writerIndex += len;
				this.writerIndex &= this.mask;
				return len;
			} else if (this.writerIndex < this.readerIndex - 1) {
				int len = Math.min(this.readerIndex - this.writerIndex - 1, length);
				System.arraycopy(data, index, this.buffer, this.writerIndex, len);
				this.writerIndex += len;
				return len;
			} else {
				int len = Math.min(this.capacity - this.writerIndex, length);
				System.arraycopy(data, index, this.buffer, this.writerIndex, len);
				this.readerIndex += len;
				this.writerIndex += len;
				this.readerIndex &= this.mask;
				this.writerIndex &= this.mask;
				return len;
			}
		}
	}
	
	public void writeFully(byte[] data, int index, int length) {
		checkBounds(data.length, index, length);
		do {
			int write = write(data, index, length);
			index  += write;
			length -= write; 
		} while (length > 0);
	}
	
	public int readFully(byte[] data, int index, int length) {
		checkBounds(data.length, index, length);
		int idx = index;
		int read;
		do {
			read = read(data, index, length);
			index  += read;
			length -= read; 
		} while (length > 0 && read > 0);
		return index - idx;
	}
	
	public boolean offer(byte b) {
		int readables = readables();
		this.write(new byte[] {b}, 0, 1);
		return readables() != readables;
	}
	
	public boolean isEmpty() {
		return readables() == 0;
	}
	
	@Override
	public String toString() {
		return String.format("RingBuffer [capacity=%s, readerIndex=%s, writerIndex=%s]", this.capacity, this.readerIndex, this.writerIndex);
	}
	
	private static void checkBounds(int arrlen, int index, int length) {
		if (index < 0 || index + length > arrlen) throw new ArrayIndexOutOfBoundsException();
	}
}
