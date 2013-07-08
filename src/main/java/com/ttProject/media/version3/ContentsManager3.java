package com.ttProject.media.version3;

import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ttProject.util.BufferUtil;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.IAtomAnalyzer;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.mp4.atom.Tkhd;
import com.ttProject.media.mp4.atom.Trak;
import com.ttProject.media.version.IContentsManager;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * コンテンツマネージャー
 * mp4のatomタグ名を書き換えることで映像をoffにする
 * @author taktod
 */
public class ContentsManager3 implements IAtomAnalyzer, IContentsManager {
	// 動作対象uri
	private final String uri;
	// freeに書き換えるべきtrakのタグの位置
	private List<Integer> skipTagPos = new ArrayList<Integer>();
	// 現在解析中のtrakデータ保持
	private Trak currentTrak;
	// 応答補助
	private int start, end;
	/**
	 * コンストラクタ
	 * @param channel
	 * @throws Exception
	 */
	public ContentsManager3(String uri) throws Exception {
		this.uri = uri;
		analyze();
	}
	/**
	 * ターゲットの解析する
	 */
	public void analyze() throws Exception {
		IReadChannel channel = FileReadChannel.openFileReadChannel(uri);
		// データを解析しておきます。
		while(analyze(channel) != null) {
			;
		}
	}
	/**
	 * メディアデータを応答します。
	 * @param request
	 * @param response
	 */
	public void accessMediaData(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		IReadChannel channel = null;
		try {
			channel = FileReadChannel.openFileReadChannel(uri);
			replyHeader(channel, request, response);
			replyBody(channel, request, response);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			if(channel != null) {
				try {
					channel.close();
					channel = null;
				}
				catch(Exception e) {
				}
			}
		}
	}
	/**
	 * 解析
	 */
	public Atom analyze(IReadChannel ch) throws Exception {
		if(ch.size() == ch.position()) {
			return null;
		}
		int position = ch.position();
		ByteBuffer buffer = BufferUtil.safeRead(ch, 8);
		int size = buffer.getInt();
		byte[] name = new byte[4];
		buffer.get(name);
		String tag = (new String(name)).toLowerCase();
		if("tkhd".equals(tag)) {
			Tkhd tkhd = new Tkhd(position, size);
			tkhd.analyze(ch);
			if(tkhd.getVolume() != 0) {
				// 音声トラックの位置をみつけた。
				skipTagPos.add(currentTrak.getPosition() + 4);
			}
			ch.position(position + size);
			return tkhd;
		}
		if("trak".equals(tag)) {
			Trak trak = new Trak(position, size);
			trak.analyze(ch, this);
			ch.position(position + size);
			currentTrak = trak;
			return trak;
		}
		if("moov".equals(tag)) {
			Moov moov = new Moov(position, size);
			moov.analyze(ch, this);
			ch.position(position + size);
			return moov;
		}
		// 適当なクラスをつくっておく。
		Atom atom = new Atom(tag, position, size) {
			@Override
			public void analyze(IReadChannel ch, IAtomAnalyzer analyzer)
					throws Exception {
			}
		};
		ch.position(position + size);
		return atom;
	}
	/**
	 * ヘッダ応答
	 * @param channel
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	public void replyHeader(IReadChannel channel, HttpServletRequest request, HttpServletResponse response) throws Exception {
		String range = request.getHeader("Range");
		if(range == null) {
			// 全データを読み込む場合
			response.setStatus(HttpServletResponse.SC_OK);
			start = 0;
			end = channel.size() - 1;
		}
		else {
			// rangeを利用した一部のデータのみ読み込む場合
			Pattern p = Pattern.compile("bytes=(\\d*)-(\\d*)");
			Matcher m = p.matcher(range);
			if(!m.matches()) {
				throw new Exception("rangeの呼び出しがおかしいです。");
			}
			if("".equals(m.group(1))) {
				start = 0;
			}
			else {
				start = Integer.parseInt(m.group(1));
			}
			if("".equals(m.group(2))) {
				end = channel.size() - 1;
			}
			else {
				end = Integer.parseInt(m.group(2));
			}
			response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
			response.addHeader("Content-Range", "bytes " + start + "-" + end + "/" + channel.size());
		}
		response.setContentLength(end + 1 - start);
		response.addHeader("ETag", "12345" + this.hashCode()); // eTagは適当にやっとく。
		response.addHeader("Accept-Ranges", "bytes");
		response.setContentType("audio/mp4");
	}
	/**
	 * データ応答
	 * @param channel
	 * @param request
	 * @param response
	 * @throws Exception
	 */
	public void replyBody(IReadChannel channel, HttpServletRequest request, HttpServletResponse response) throws Exception {
		WritableByteChannel target = null;
		channel.position(start);
		try {
			// 問題のデータを読み込んで応答します。
			target = Channels.newChannel(response.getOutputStream());
			// コピー中に、httpが切れる可能性があるので、その辺り注意しておく。
			for(int tagPos : skipTagPos) {
				// endの方がtagPosより前だったらそこまでにしなければいけない・・・
				if(start < tagPos) {
					// tagPosまで応答する
					BufferUtil.quickCopy(channel, target, tagPos - start);
					start = tagPos;
				}
				if(start < tagPos + 4) {
					int replySize = 4 + tagPos - start;
					// tagの内部だった場合はtagの内容を応答する。
					ByteBuffer buffer = ByteBuffer.allocate(replySize);
					buffer.put("free".substring(4 - replySize).getBytes());
					buffer.flip();
					target.write(buffer);
					start += replySize;
					channel.position(start);
				}
			}
			// tagを挿入すべき場所からぬけた場合は最後まで応答する。
			BufferUtil.quickCopy(channel, target, end + 1 - start);
		}
		catch(Exception e) {
			// partial contentは途中で転送切れを起こすことがあります。
			// すると例外がでます。
//			e.printStackTrace();
		}
		finally {
			if(target != null) {
				try {
					target.close();
					target = null;
				}
				catch(Exception e) {
				}
			}
		}
	}
}
