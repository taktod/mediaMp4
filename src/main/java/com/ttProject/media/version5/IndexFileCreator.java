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
import com.ttProject.media.test.Sond;
import com.ttProject.media.test.Vdeo;
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
	private Vdeo vdeo;
	private Sond sond;
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
		idx = new FileOutputStream(targetFile + ".idx").getChannel();
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
			// trakの開始位置を調べる。
			this.trakStartPos = (int)idx.position();
			Trak trak = new Trak(size, position);
			trak.analyze(ch, this);
			ch.position(position + size);
			return trak;
		case Tkhd: // 今回はこれがいる。
			Tkhd tkhd = new Tkhd(size, position);
			tkhd.analyze(ch);
			ch.position(position + size);
			return tkhd;
		case Mdia:
			Mdia mdia = new Mdia(size, position);
			mdia.analyze(ch, this);
			ch.position(position + size);
			return mdia;
		case Mdhd:
			Mdhd mdhd = new Mdhd(size, position);
			System.out.println("mdhd");
			mdhd.analyze(ch, null);
			ch.position(position + size);
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
			this.vdeo = new Vdeo(0, this.trakStartPos);
			this.vdeo.makeTag(idx);
			Vmhd vmhd = new Vmhd(size, position);
			ch.position(position + size);
			return vmhd;
		case Smhd:
			this.sond = new Sond(0, this.trakStartPos);
			this.sond.makeTag(idx);
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
			System.out.println("解析開始:" + Integer.toHexString(position));
			try {
				stsd.analyze(ch, new RecordAnalyzer());
				// 適合している場合はmshを取り出す。
				for(Record record : stsd.getRecords()) {
					if(record instanceof H264) {
						H264 h264 = (H264)record;
						Avcc avcc = h264.getAvcc();
						// そのままコピーしておく。
//						avcc.copy(ch, idx);
						buffer = ByteBuffer.allocate(8);
						buffer.putInt(avcc.getSize());
						buffer.put("msh ".getBytes());
						buffer.flip();
						idx.write(buffer);
						ch.position(avcc.getPosition() + 8);
						BufferUtil.quickCopy(ch, idx, avcc.getSize() - 8);
					}
					else if(record instanceof Aac) {
						Aac aac = (Aac)record;
						byte[] data = aac.getEsds().getSequenceHeader();
						buffer = ByteBuffer.allocate(8 + data.length);
						buffer.putInt(8 + data.length);
						buffer.put("msh ".getBytes());
						buffer.put(data);
						buffer.flip();
						idx.write(buffer);
					}
				}
			}
			catch(Exception e) {
				System.out.println("flvに適合しないコーデックタグをみつけたっぽい。");
				e.printStackTrace();
				// 適合していない場合は開始位置までfileを削っておく
				System.out.println("trakStartPos:" + trakStartPos);
				idx.truncate(trakStartPos);
				idx.position(trakStartPos);
				return null;
			}
			System.out.println("解析おわり");
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
