package com.ttProject.media.version5;

import java.io.File;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ttProject.media.flv.Tag;
import com.ttProject.media.mp4.Atom;
import com.ttProject.media.mp4.atom.Moov;
import com.ttProject.media.version.IContentsManager;
import com.ttProject.nio.channels.FileReadChannel;
import com.ttProject.nio.channels.IFileReadChannel;
import com.ttProject.util.TmpFile;

public class ContentsManager5 implements IContentsManager {
	/** 動作対象uri */
	private final String uri;
	private final TmpFile idxFile;
	/**
	 * コンストラクタ
	 * @param uri
	 * @throws Exception
	 */
	public ContentsManager5(String uri) throws Exception {
		this.uri = uri;
		File f = new File(uri);
		this.idxFile = new TmpFile("/tmp/" + f.getName() + ".m4i");
		analyze();
	}
	@Override
	public void analyze() throws Exception {
		IFileReadChannel source = FileReadChannel.openFileReadChannel(uri);
		IndexFileCreator analyzer = new IndexFileCreator(idxFile);
		Atom atom = null;
		while((atom = analyzer.analyze(source)) != null) {
			if(atom instanceof Moov) {
				break;
			}
		}
		analyzer.updatePrevTag();
		analyzer.checkDataSize();
		analyzer.close();
	}
	@Override
	public void accessMediaData(final HttpServletRequest request,
			final HttpServletResponse response) throws Exception {
//		System.out.println(request.getQueryString());
		String startPos = request.getParameter("start");
		if(startPos == null) {
			startPos = "0";
		}
		System.out.println(startPos);
		IFileReadChannel source = null, tmp = null;
		int responseSize = 0;
		try {
			final WritableByteChannel target = Channels.newChannel(response.getOutputStream());
			source = FileReadChannel.openFileReadChannel(uri);
			tmp = FileReadChannel.openFileReadChannel(idxFile.getAbsolutePath());
			final FlvOrderModel orderModel = new FlvOrderModel(tmp, true, true, Integer.parseInt(startPos));
			orderModel.addStartEvent(new IFlvStartEventListener() {
				@Override
				public void start(int responseSize) {
					System.out.println("応答用のデータ準備した。:" + responseSize);
					// 応答すべきデータ量が決定したときに処理される動作
					// レスポンスデータ量とかが決定される。
					response.setStatus(HttpServletResponse.SC_OK);
					response.setContentLength(responseSize);
					response.setContentType("video/x-flv");
					try {
						orderModel.getFlvHeader().writeTag(target);
					}
					catch (Exception e) {
						e.printStackTrace();
					}
				}
			});
			List<Tag> tagList;
			while((tagList = orderModel.nextTagList(source)) != null) {
				for(Tag tag : tagList) {
					responseSize += tag.getSize();
					tag.writeTag(target);
				}
			}
		}
		catch (Exception e) {
//			e.printStackTrace();
			System.out.println("応答したデータ量:" + responseSize);
		}
		finally {
			if(source != null) {
				try {
					source.close();
				}
				catch(Exception e) {
				}
				source = null;
			}
			if(tmp != null) {
				try {
					tmp.close();
				}
				catch(Exception e) {
				}
				tmp = null;
			}
		}
	}
}
