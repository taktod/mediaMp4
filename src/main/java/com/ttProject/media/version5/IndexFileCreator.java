package com.ttProject.media.version5;

import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.Type;
import com.ttProject.media.mp4.atom.Ftyp;
import com.ttProject.media.mp4.atom.Mdhd;
import com.ttProject.media.mp4.atom.Mdia;
import com.ttProject.media.mp4.atom.Minf;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Smhd;
import com.ttProject.media.mp4.atom.Stbl;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stsd;
import com.ttProject.media.mp4.atom.Stss;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Stts;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.mp4.atom.Vmhd;
import com.ttProject.media.mp4.atom.stsd.Record;
import com.ttProject.media.mp4.atom.stsd.RecordAnalyzer;
import com.ttProject.media.mp4.atom.stsd.data.Avcc;
import com.ttProject.media.mp4.atom.stsd.record.Aac;
import com.ttProject.media.mp4.atom.stsd.record.H264;
import com.ttProject.media.version5.mp4.Meta;
import com.ttProject.media.version5.mp4.Sond;
import com.ttProject.media.version5.mp4.Vdeo;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;

/**
 * indexファイルを作成していきます。
 * @author taktod
 */
public class IndexFileCreator implements IAtomAnalyzer {
	private FileChannel idx; // 書き込み対象ファイル
	private int trakStartPos; // トラックの開始位置
	private CurrentType type = null; // 現在の処理trakタイプ
	private Vdeo vdeo = null;
	private Sond sond = null;
	private Meta meta = null;
	private Mdhd mdhd = null;
	private Tkhd tkhd = null;
	private enum CurrentType { // タイプリスト
		AUDIO,
		VIDEO,
		HINT,
		MEDIA
	}
	/**
	 * コンストラクタ
	 * @param targetFile
	 * @throws Exception
	 */
	public IndexFileCreator(String targetFile) throws Exception {
		idx = new FileOutputStream(targetFile).getChannel();
	}
	@Override
	public Atom analyze(IFileReadChannel ch) throws Exception {
		if(ch.size() == ch.position()) {
			return null;
		}
		int position = ch.position();
		ByteBuffer buffer = BufferUtil.safeRead(ch, 8);
		int size = buffer.getInt();
		String tag = BufferUtil.getDwordText(buffer);
		Type type = Type.getType(tag);
		switch(type) {
		case Ftyp:
			Ftyp ftyp = new Ftyp(size, position);
			ch.position(position + size);
			return ftyp;
		case Moov:
			Moov moov = new Moov(size, position);
			moov.analyze(ch, this);
			ch.position(position + size);
			return moov;
/*		case Mvhd:
			Mvhd mvhd = new Mvhd(size, position);
			// とりあえず解析せずほっとく
			ch.position(position + size);
			return mvhd;
/*		case Iods: // 消す候補
			// iodsは必要ないと思う。消す。
			Iods iods = new Iods(size, position);
			ch.position(position + size);
			return iods;
		case Udta: // 消す候補
			// udtaはいらない。
			Udta udta = new Udta(size, position);
			ch.position(position + size);
			return udta;*/
		case Trak:
			updatePrevTag();
			this.type = null;
			// trakの開始位置を調べる。
			this.trakStartPos = (int)idx.position();
			Trak trak = new Trak(size, position);
			trak.analyze(ch, this);
			ch.position(position + size);
			return trak;
		case Tkhd: // 今回はこれがいる。
			tkhd = new Tkhd(size, position);
			tkhd.analyze(ch);
			ch.position(position + size);
			return tkhd;
		case Mdia:
			Mdia mdia = new Mdia(size, position);
			mdia.analyze(ch, this);
			ch.position(position + size);
			return mdia;
		case Mdhd:
			mdhd = new Mdhd(size, position);
			mdhd.analyze(ch, null);
			ch.position(position + size);
			if(tkhd.getHeight() != 0 && tkhd.getWidth() != 0) {
				meta = new Meta(28, this.trakStartPos);
				meta.setHeight(tkhd.getHeight());
				meta.setWidth(tkhd.getWidth());
				meta.setDuration(mdhd.getDuration() * 1000 / mdhd.getTimescale());
			}
			return mdhd;
/*		case Hdlr:
			Hdlr hdlr = new Hdlr(size, position);
			ch.position(position + size);
			return hdlr;*/
		case Minf:
			Minf minf = new Minf(size, position);
			minf.analyze(ch, this);
			ch.position(position + size);
			return minf;
		case Vmhd:
			this.type = CurrentType.VIDEO;
			this.vdeo = new Vdeo(0, this.trakStartPos);
			this.vdeo.setTimescale(mdhd.getTimescale());
			this.vdeo.writeIndex(idx);
			Vmhd vmhd = new Vmhd(size, position);
			ch.position(position + size);
			return vmhd;
		case Smhd:
			this.type = CurrentType.AUDIO;
			this.sond = new Sond(0, this.trakStartPos);
			this.sond.setTimescale(mdhd.getTimescale());
			this.sond.writeIndex(idx);
			Smhd smhd = new Smhd(size, position);
			ch.position(position + size);
			return smhd;
/*		case Dinf:
			Dinf dinf = new Dinf(size, position);
			ch.position(position + size);
			return dinf;*/
		case Stbl:
			Stbl stbl = new Stbl(size, position);
			stbl.analyze(ch, this);
			ch.position(position + size);
			return stbl;
		case Stsd:
			Stsd stsd = new Stsd(size, position);
			try {
				stsd.analyze(ch, new RecordAnalyzer());
				// 適合している場合はmshを取り出す。
				for(Record record : stsd.getRecords()) {
					if(record instanceof H264) {
						H264 h264 = (H264)record;
						Avcc avcc = h264.getAvcc();
						// そのままコピーしておく。
						buffer = ByteBuffer.allocate(8);
						buffer.putInt(avcc.getSize());
						buffer.put("msh ".getBytes());
						buffer.flip();
						idx.write(buffer);
						ch.position(avcc.getPosition() + 8);
						BufferUtil.quickCopy(ch, idx, avcc.getSize() - 8);
					}
					else if(record instanceof Aac) {
						// どうやらavconvでmp3に変換したらrecordタグはmp4aになるみたい。
						// その場合mshはnullになってしまう。
						Aac aac = (Aac)record;
						// TODO この上書きの部分が少々気に入らない
						// このタイミングでsondの中にデータをいれておく。
						System.out.println("sampleRate:" + aac.getSampleRate());
						System.out.println("channels:" + aac.getChannelCount());
						long prevPos = idx.position();
						idx.position(sond.getPosition() + 20);
						buffer = ByteBuffer.allocate(5);
						buffer.putInt(aac.getSampleRate());
						buffer.put((byte)aac.getChannelCount());
						buffer.flip();
						idx.write(buffer);
						idx.position(prevPos);
						byte[] data = aac.getEsds().getSequenceHeader();
						if(data != null) {
							buffer = ByteBuffer.allocate(8 + data.length);
							buffer.putInt(8 + data.length);
							buffer.put("msh ".getBytes());
							buffer.put(data);
							buffer.flip();
							idx.write(buffer);
						}
					}
				}
			}
			catch(Exception e) {
				this.type = null;
				e.printStackTrace();
				// 適合していない場合は開始位置までfileを削っておく
				idx.truncate(trakStartPos);
				idx.position(trakStartPos);
				return null;
			}
			ch.position(position + size);
			return stsd;
		case Stts:
			Stts stts = new Stts(size, position);
			buffer.position(0);
			idx.write(buffer);
			BufferUtil.quickCopy(ch, idx, size - 8);
			ch.position(position + size);
			return stts;
		case Stss: // keyFrameのデータになる必須
			// 映像の場合はkeyFrame指示になるっぽい
			Stss stss = new Stss(size, position);
			buffer.position(0);
			idx.write(buffer);
			BufferUtil.quickCopy(ch, idx, size - 8);
			ch.position(position + size);
			return stss;
		case Stsc:
			// 各チャンクのサンプル量
			Stsc stsc = new Stsc(size, position);
			buffer.position(0);
			idx.write(buffer);
			BufferUtil.quickCopy(ch, idx, size - 8);
			ch.position(position + size);
			return stsc;
		case Stsz:
			// サンプルのサイズ量
			Stsz stsz = new Stsz(size, position);
			buffer.position(0);
			idx.write(buffer);
			BufferUtil.quickCopy(ch, idx, size - 8);
			ch.position(position + size);
			return stsz;
		case Stco:
			// 各チャンクの開始位置
			Stco stco = new Stco(size, position);
			buffer.position(0);
			idx.write(buffer);
			BufferUtil.quickCopy(ch, idx, size - 8);
			ch.position(position + size);
			return stco;
/*		case Mdat:
			Mdat mdat = new Mdat(size, position);
			// mdatの位置を考えることでmoovが後ろにあるmp4でも対応できるようになる。(ただし処理がおそくなる)
			ch.position(position + size);
			return mdat;*/
		}
		ch.position(position + size);
		return new Atom(tag, size, position) {
			@Override
			public void analyze(IFileReadChannel ch, IAtomAnalyzer analyzer)
					throws Exception {
				;
			}
		};
	}
	public void updatePrevTag() throws Exception {
		if(this.type == CurrentType.AUDIO
		|| this.type == CurrentType.VIDEO) {
			// いままでよんできたデータが正しいtagだった場合
			int prevPosition = (int)idx.position();
			int prevSize = prevPosition - this.trakStartPos;
			ByteBuffer buf = ByteBuffer.allocate(4);
			buf.putInt(prevSize);
			buf.flip();
			idx.position(trakStartPos);
			idx.write(buf);
			idx.position(prevPosition);
		}
		if(meta != null) {
			// metaデータを書き込んでおく。
			meta.writeIndex(idx);
			meta = null;
		}
	}
	public void close() {
		if(idx != null) {
			try {
				idx.close();
			}
			catch(Exception e) {
			}
			idx = null;
		}
	}
}
