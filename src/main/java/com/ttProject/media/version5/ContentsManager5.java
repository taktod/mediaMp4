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
		analyzer.close();
	}
	@Override
	public void accessMediaData(HttpServletRequest request,
			HttpServletResponse response) throws Exception {
		System.out.println(request.getQueryString());
		IFileReadChannel source = null, tmp = null;
		try {
			source = FileReadChannel.openFileReadChannel(uri);
			tmp = FileReadChannel.openFileReadChannel(idxFile.getAbsolutePath());
			FlvOrderModel orderModel = new FlvOrderModel(tmp, true, true, 0);
			WritableByteChannel target = Channels.newChannel(response.getOutputStream());
			orderModel.getFlvHeader().writeTag(target);
			List<Tag> tagList;
			while((tagList = orderModel.nextTagList(source)) != null) {
				for(Tag tag : tagList) {
					tag.writeTag(target);
				}
			}
		}
		catch (Exception e) {
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
