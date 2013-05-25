package com.ttProject.media.version4;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.ParentAtom;
import com.ttProject.media.mp4.atom.Iods;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Stco;
import com.ttProject.media.mp4.atom.Stsc;
import com.ttProject.media.mp4.atom.Stsz;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.mp4.atom.Udta;
import com.ttProject.media.version.IContentsManager;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.BufferUtil;
import com.ttProject.util.ChannelUtil;
import com.ttProject.util.TmpFile;

/**
 * mp4のデータから映像の部分を撤去して応答するプログラム
 * @author taktod
 */
public class ContentsManager4_bku implements IContentsManager {
	/** 替わりに設定するftyp値 */
	private static byte[] ftyp = {
		0x00, 0x00, 0x00, 0x1C,
		'f', 't', 'y', 'p',
		'M', '4', 'A', ' ',
		0x00, 0x00, 0x00, 0x00,
		'i', 's', 'o', 'm',
		'M', '4', 'A', ' ',
		'm', 'p', '4', '2'
	};
	// 動作対象uri
	private final String uri;
	private File hdrFile;
	private File idxFile;

	// 解析補助
	private IFileReadChannel source = null;
	private Stsc stsc = null;
	private Stsz stsz = null;
	private Stco stco = null;
	
	/**
	 * コンストラクタ
	 * @param uri
	 * @throws Exception
	 */
	public ContentsManager4_bku(String uri) throws Exception {
		this.uri = uri;
		// hdrとidxを作成する必要があります。
		File f = new File(uri);
		hdrFile = new TmpFile("/tmp/" + f.getName() + ".hdr");
		idxFile = new TmpFile("/tmp/" + f.getName() + ".idx");
		try {
			System.out.println("analyze開始");
			analyze();
			System.out.println("analyzeおわり");
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			stsc = null;
			stsz = null;
			stco = null;
		}
	}
	/**
	 * あらかじめ実行する解析動作
	 */
	public void analyze() throws Exception {
		// まず高速でhdrの全体の量がどうなるか確認する必要がある
		try {
			source = FileReadChannel.openFileReadChannel(uri);
			Moov moov = findMoov(source);
			// hdrの中身になるデータをソートしていく。
			List<Atom> list = new ArrayList<Atom>();
			int moovSize = sortAtoms(moov, list, 8);
			// stsc stsz stco co64があるか確認
			// co64はidxファイルがlongに対応させていないので、NGにしました。
			if(stsc == null || stsz == null || stco == null) {
				throw new Exception("mp4 data is corrupted.");
			}
			// 続いてhdrの中身を構築していく必要がある。
			makeTmpFile(list, moovSize);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			source = ChannelUtil.safeClose(source);
		}
	}
	/**
	 * @param source
	 * @param list
	 * @param size
	 */
	private void makeTmpFile(List<Atom> list, int moovSize) throws Exception {
		FileChannel idx = null;
		FileChannel hdr = null;
		IFileReadChannel stscReader = null;
		IFileReadChannel stszReader = null;
		ByteBuffer buffer;
		int pos;
		try {
			idx = new FileOutputStream(idxFile).getChannel();
			hdr = new FileOutputStream(hdrFile).getChannel();
			// 全体の長さ書き込み(とりあえずデフォルトとして、全体サイズをいれておく)
			BufferUtil.writeInt(idx, source.size());
			// ftyp
			buffer = ByteBuffer.allocate(ftyp.length + 8);
			buffer.put(ftyp);
			// moov
			buffer.putInt(moovSize);
			buffer.put("moov".getBytes());
			buffer.flip();
			hdr.write(buffer);
			// 通常のタグの書き込みをすすめていく。
			for(Atom atom : list) {
				if(atom instanceof ParentAtom) {
					buffer = ByteBuffer.allocate(8);
					buffer.putInt(atom.getSize());
					buffer.put(atom.getName().getBytes());
					buffer.flip();
					hdr.write(buffer);
				}
				else {
					atom.copy(source, hdr);
				}
			}
			// 残りはstcoの書き込みとidxファイルの作成
			IFileReadChannel stcoReader = source;
			stcoReader.position(stco.getPosition());
			// stcoの先頭部分コピー
			buffer = BufferUtil.safeRead(stcoReader, 12);
			hdr.write(buffer);
			buffer = BufferUtil.safeRead(stcoReader, 4);
			hdr.write(buffer);
			buffer.position(0); // stcoのcountはhdrにも書き込んでおく。
			idx.write(buffer);

			// stcoのindex
			pos = ftyp.length + moovSize + 8;
			stscReader = FileReadChannel.openFileReadChannel(uri, stsc.getPosition() + 12);
			int stscCount = BufferUtil.safeRead(stscReader, 4).getInt();
			stszReader = FileReadChannel.openFileReadChannel(uri, stsz.getPosition() + 12);
			buffer = BufferUtil.safeRead(stszReader, 8);
			int stszConstant = buffer.getInt();
			
			int mdatSize = 0; // mdatのサイズ計算用
			int currentChunk = 1; // 現在処理中のchunk値
			int currentSampleCount = 0; // 現在のサンプル値
			// stscの数繰り返す
			for(int i = 0;i < stscCount;i ++) {
				buffer = BufferUtil.safeRead(stscReader, 12);
				int chunk = buffer.getInt();
				for(;currentChunk < chunk;currentChunk ++) {
					int chunkSize = calcurateChunkSize(
							idx, hdr,
							stszReader, stcoReader,
							stszConstant, currentSampleCount, pos);
					pos += chunkSize;
					mdatSize += chunkSize;
				}
				currentSampleCount = buffer.getInt();
//				buffer.getInt(); // ref番号
			}
			int chunkSize = calcurateChunkSize(
					idx, hdr,
					stszReader, stcoReader,
					stszConstant, currentSampleCount, pos);
			pos += chunkSize;
			mdatSize += chunkSize;

			// mdatの先頭の部分を書き込んでおく。
			buffer = ByteBuffer.allocate(8);
			buffer.putInt(mdatSize + 8);
			buffer.put("mdat".getBytes());
			buffer.flip();
			hdr.write(buffer);
			idx.position(0);
			BufferUtil.writeInt(idx, pos);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			stscReader = ChannelUtil.safeClose(stscReader);
			stszReader = ChannelUtil.safeClose(stszReader);
			idx = ChannelUtil.safeClose(idx);
			hdr = ChannelUtil.safeClose(hdr);
		}
	}
	private int calcurateChunkSize(FileChannel idx, FileChannel hdr,
			IFileReadChannel stszReader, IFileReadChannel stcoReader,
			int stszConstant, int currentSampleCount, int pos) throws Exception,
			IOException {
		int chunkSize = 0;
		for(int i = 0;i < currentSampleCount;i ++) {
			if(stszConstant == 0) {
				chunkSize += BufferUtil.safeRead(stszReader, 4).getInt();
			}
			else {
				chunkSize += stszConstant;
			}
		}
		BufferUtil.writeInt(hdr, pos);
		BufferUtil.writeInt(idx, pos);
		idx.write(BufferUtil.safeRead(stcoReader, 4));
		BufferUtil.writeInt(idx, chunkSize);
		return chunkSize;
	}
	/**
	 * moovをみつける。
	 * @param source
	 * @return
	 * @throws Exception
	 */
	private Moov findMoov(IFileReadChannel source) throws Exception {
		IAtomAnalyzer analyzer = new AtomAnalyzer();
		Atom atom = null;
		while((atom = analyzer.analyze(source)) != null) {
			if(atom instanceof Moov) {
				return (Moov)atom;
			}
		}
		throw new Exception("moov is undefined");
	}
	/**
	 * 必要なatom抽出していきます
	 * @param parent
	 * @param list
	 * @param pos
	 * @return 位置情報
	 */
	private int sortAtoms(ParentAtom parent, List<Atom> list, int pos) {
		ParentAtom next = null;
		for(Atom atom : parent.getAtoms()) {
			if(atom instanceof Trak) { // trakは音声トラックであるか確認
				Trak t = (Trak)atom;
				// Tkhdをもっているか確認。
				boolean findTkhd = false;
				for(Atom a : t.getAtoms()) {
					if(a instanceof Tkhd) {
						findTkhd = true;
						break;
					}
				}
				if(!findTkhd) { // tkhdがない場合は映像トラック
					continue;
				}
				next = t;
				continue;
			}
			else if(atom instanceof Iods || atom instanceof Udta) {
				// IodsとUdtaは削除する。
				continue;
			}
			else if(atom instanceof Stsc) { // stscはあとで解析するので保持
				stsc = (Stsc)atom;
			}
			else if(atom instanceof Stsz) { // stszも解析するので保持
				stsz = (Stsz)atom;
			}
			else if(atom instanceof Stco) { // stcoはあとで計算しなおす必要ある
				stco = (Stco)atom;
				pos += atom.getSize();
				continue;
			}
			else if(atom instanceof ParentAtom) { // 子要素を持つAtom
				next = (ParentAtom)atom;
				continue;
			}
			pos += atom.getSize();
			list.add(atom);
		}
		if(next != null) { // 同じ階層のデータ処理がおわったら子要素の調査に進む
			list.add(next);
			pos += 8;
			pos = sortAtoms(next, list, pos);
		}
		return pos;
	}
	/**
	 * 実際のメディアへのアクセス
	 */
	public void accessMediaData(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		long startTime = System.currentTimeMillis();
		while(!idxFile.exists() || !hdrFile.exists()) {
			Thread.sleep(100);
			if(System.currentTimeMillis() - startTime > 10000) {
				throw new Exception("timeout");
			}
		}
		IFileReadChannel idxChannel = null;
		IFileReadChannel hdrChannel = null;
		WritableByteChannel channel = null;
		// 等々レスポンスの部分をつくっていきます。
		try {
			idxChannel = FileReadChannel.openFileReadChannel(idxFile.getAbsolutePath());
			hdrChannel = FileReadChannel.openFileReadChannel(hdrFile.getAbsolutePath());
			channel = Channels.newChannel(response.getOutputStream());
			responseHeader(request, response, idxChannel, hdrChannel, channel);
		}
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			idxChannel = ChannelUtil.safeClose(idxChannel);
			hdrChannel = ChannelUtil.safeClose(hdrChannel);
		}
	}
	/**
	 * 応答データを作成
	 * @param request
	 * @param response
	 * @param idxChannel
	 * @param hdrChannel
	 * @param channel
	 * @param start
	 * @param end
	 * @throws Exception
	 */
	private void responseData(HttpServletRequest request,
			HttpServletResponse response,
			IFileReadChannel idxChannel,
			IFileReadChannel hdrChannel,
			WritableByteChannel channel,
			int start, int end) throws Exception {
		ByteBuffer buffer;
		// 実データを取得する動作
		while(true) {
			// headerの読み込みか確認する。
			if(start >= hdrChannel.size()) {
				break;
			}
			int a = (int)hdrChannel.size() - start;
			int b = end - start + 1;
			int c = 65536;
			int d = (a < b ? a : b);
			d = (d < c ? d : c);
			hdrChannel.position(start);
			buffer = BufferUtil.safeRead(hdrChannel, d);
			if(buffer.remaining() == 0) {
				return;
			}
			start += buffer.remaining();
			channel.write(buffer);
		}
		// headerのファイル外を読み込むことになった。
		idxChannel.position(8);
		int newStart;
		int originalStart;
		int chunkSize;
		while(true) {
			buffer = BufferUtil.safeRead(idxChannel, 12);
			newStart = buffer.getInt();
			originalStart = buffer.getInt();
			chunkSize = buffer.getInt();
			if(newStart <= start && start < newStart + chunkSize) {
				// 開始位置が元のデータのchunkの中になっている部分発見
				break;
			}
		}
		IFileReadChannel source = null;
		try {
			// オリジナルファイル上の開始位置をしっておく必要がある
			source = FileReadChannel.openFileReadChannel(uri, start - newStart + originalStart);
			int chunkToRead = chunkSize + newStart - start;
			while(true) {
				int leftSize = end + 1 - start;
				if(leftSize <= chunkToRead) {
					BufferUtil.quickCopy(source, channel, leftSize);
					// ここでおわり
					break;
				}
				// chunkToRead分読み込む
				BufferUtil.quickCopy(source, channel, chunkToRead);
				start += chunkToRead;
				// スキップすべきデータ量をスキップする。
				// 次の位置を読み込んで
				if(idxChannel.position() == idxChannel.size()) {
					return;
				}
				buffer = BufferUtil.safeRead(idxChannel, 12);
				newStart = buffer.getInt();
				int prevOriginalStart = originalStart;
				int prevSize = chunkSize;
				originalStart = buffer.getInt();
				chunkSize = buffer.getInt();
				buffer = BufferUtil.safeRead(source, originalStart - prevOriginalStart - prevSize);
				chunkToRead = chunkSize;
			}
		}
		catch(Exception e) {
			;
		}
		finally {
			source = ChannelUtil.safeClose(source);
		}
	}
	/**
	 * 応答ヘッダを作成
	 * @param request
	 * @param response
	 * @param idxChannel
	 * @param hdrChannel
	 * @param channel
	 * @throws Exception
	 */
	private void responseHeader(HttpServletRequest request,
			HttpServletResponse response,
			IFileReadChannel idxChannel,
			IFileReadChannel hdrChannel,
			WritableByteChannel channel) throws Exception {
		// まず応答ヘッダをつくる。
		String range = request.getHeader("Range");
		int contentLength = BufferUtil.safeRead(idxChannel, 4).getInt();
		int start = 0;
		int end = 0;
		if(range == null) {
			response.setStatus(HttpServletResponse.SC_OK);
			start = 0;
			end = contentLength - 1;
		}
		else {
			Pattern p = Pattern.compile("bytes=(\\d*)-(\\d*)");
			Matcher m = p.matcher(range);
			if(!m.matches()) {
				throw new Exception("rangeの呼び出しがおかしい。");
			}
			if("".equals(m.group(1))) {
				start = 0;
			}
			else {
				start = Integer.parseInt(m.group(1));
			}
			if("".equals(m.group(2))) {
				end = contentLength - 1;
			}
			else {
				end = Integer.parseInt(m.group(2));
			}
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
			response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + contentLength);
		}
		response.setContentLength(end + 1 - start);
		response.addHeader("ETag", "12345" + uri);
		response.addHeader("Accept-Ranges", "bytes");
		response.setContentType("audio/mp4");
		responseData(request, response, idxChannel, hdrChannel, channel, start, end);
	}
}
