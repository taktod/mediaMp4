package com.ttProject.media.test;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stss;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Stts;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

public class Sond extends Atom {
	private int size;
	private Msh msh;
	private Stco stco;
	private Stsc stsc;
	private Stsz stsz;
	private Stts stts;
	private int responseSize;
	private final List<Atom> atoms = new ArrayList<Atom>();
	public Sond(int size, int position) {
		super(Sond.class.getSimpleName().toLowerCase(), size, position);
		this.size = size;
	}
	public void setSize(int size) {
		this.size = size;
	}
	public int getSize() {
		return size;
	}
	public void addAtom(Atom atom) {
		atoms.add(atom);
	}
	public List<Atom> getAtoms() {
		return atoms;
	}
	@Override
	public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
			throws Exception {
		ch.position(getPosition() + 8);
		ByteBuffer buffer = BufferUtil.safeRead(ch, 8);
		// ここから先がタグデータ
		while(ch.position() < getPosition() + getSize()) {
			int position = ch.position();
			buffer = BufferUtil.safeRead(ch, 8);
			int size = buffer.getInt();
			String tag = BufferUtil.getDwordText(buffer);
			if("msh ".equals(tag)) {
				msh = new Msh(size, position);
			}
			else if("stco".equals(tag)) {
				stco = new Stco(size, position);
			}
			else if("stsc".equals(tag)) {
				stsc = new Stsc(size, position);
			}
			else if("stsz".equals(tag)) {
				stsz = new Stsz(size, position);
			}
			else if("stts".equals(tag)) {
				stts = new Stts(size, position);
			}
			else {
				throw new Exception("解析不能なタグを発見:" + tag);
			}
			ch.position(position + size);
		}
	}
	public void makeTag(FileChannel idx) throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(size); // サイズ
		buffer.put("sond".getBytes()); // タグ
		buffer.putInt(0); // version + flags
		buffer.putInt(0); // resSize
		buffer.flip();
		idx.write(buffer);
	}
}
