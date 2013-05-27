package com.ttProject.media.version5;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

public class Meta extends Atom {
	public int height;
	public int width;
	public long duration;
	public Meta(int size, int position) {
		super(Meta.class.getSimpleName().toLowerCase(), size, position);
	}
	public int getHeight() {
		return height;
	}
	public void setHeight(int height) {
		this.height = height;
	}
	public int getWidth() {
		return width;
	}
	public void setWidth(int width) {
		this.width = width;
	}
	public long getDuration() {
		return duration;
	}
	public void setDuration(long duration) {
		this.duration = duration;
	}
	@Override
	public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
			throws Exception {
		ch.position(getPosition() + 8);
		ByteBuffer buffer = BufferUtil.safeRead(ch, 20);
		buffer.position(4);
		width = buffer.getInt();
		height = buffer.getInt();
		duration = buffer.getLong();
	}
	public void makeTag(WritableByteChannel idx) throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(28);
		buffer.putInt(getSize());
		buffer.put("meta".getBytes());
		buffer.putInt(0); // version + flags
		buffer.putInt(width);
		buffer.putInt(height);
		buffer.putLong(duration);
		buffer.flip();
		idx.write(buffer);
	}
}
