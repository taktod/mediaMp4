package com.ttProject.media.test;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.nio.channels.IFileReadChannel;

/**
 * video要素for indexFile
 * @author taktod
 */
public class Vdeo extends Atom {
	private int size;
	private final List<Atom> atoms = new ArrayList<Atom>();
	public Vdeo(int size, int position) {
		super(Vdeo.class.getSimpleName().toLowerCase(), size, position);
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
	}
	public void makeTag(FileChannel idx) throws Exception {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(size); // サイズ
		buffer.put("vdeo".getBytes()); // タグ
		buffer.putInt(0); // version + flags
		buffer.putInt(0); // resSize
		buffer.flip();
		idx.write(buffer);
	}
}
