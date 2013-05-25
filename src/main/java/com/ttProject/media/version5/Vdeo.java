package com.ttProject.media.version5;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stss;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Stts;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * video要素for indexFile
 * @author taktod
 */
public class Vdeo extends Atom {
	private int size;
	private Msh msh;
	private Stco stco;
	private Stsc stsc;
	private Stsz stsz;
	private Stts stts;
	private Stss stss;
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
			else if("stss".equals(tag)) {
				stss = new Stss(size, position);
			}
			else {
				throw new Exception("解析不能なタグを発見:" + tag);
			}
			ch.position(position + size);
		}
	}
	public Msh getMsh() {
		return msh;
	}
	public Stco getStco() {
		return stco;
	}
	public Stsc getStsc() {
		return stsc;
	}
	public Stsz getStsz() {
		return stsz;
	}
	public Stts getStts() {
		return stts;
	}
	public Stss getStss() {
		return stss;
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
