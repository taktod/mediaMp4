package com.ttProject.media.version2;

import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ttProject.util.BufferUtil;
import com.ttProject.media.version.IContentsManager;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IReadChannel;

/**
 * コンテンツマネージャー
 * mp4のデータをそのままproxyする
 * @author taktod
 */
public class ContentsManager2 implements IContentsManager {
	// 動作対象uri
	private final String uri;
	private int start, end;
	/**
	 * コンストラクタ
	 * @param uri
	 * @throws Exception
	 */
	public ContentsManager2(String uri) throws Exception {
		this.uri = uri;
		analyze();
	}
	/**
	 * 解析実施
	 */
	public void analyze() throws Exception {
		// そのまま応答する動作なので、なにもすることはない
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
		catch (Exception e) {
			e.printStackTrace();
		}
		finally {
			if(channel != null) {
				try {
					channel.close();
					channel = null;
				}
				catch (Exception e) {
				}
			}
		}
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
		response.setContentType("video/mp4");
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
			target = Channels.newChannel(response.getOutputStream());
			BufferUtil.quickCopy(channel, target, end + 1 - start);
		}
		catch(Exception e) {
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
