package com.ttProject.media.webapp;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ttProject.media.version2.ContentsManager2;
import com.ttProject.media.version3.ContentsManager3;
import com.ttProject.media.version4.ContentsManager4;

public class Mp4Servlet extends HttpServlet {
	private static final long serialVersionUID = -5489579505092778690L;
	private ContentsManager4 manager4 = null;
	private ContentsManager3 manager3 = null;
	private ContentsManager2 manager2 = null;
	/**
	 * コンストラクタ
	 */
	public Mp4Servlet() {
		try {
			manager4 = new ContentsManager4("http://49.212.39.17/mario.mp4");
			manager3 = new ContentsManager3("http://49.212.39.17/mario.mp4");
			manager2 = new ContentsManager2("http://49.212.39.17/mario.mp4");
		}
		catch (Exception e) {
		}
	}
	/**
	 * getアクセス
	 */
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doProxyTask(req, resp);
	}
	/**
	 * postアクセス
	 */
	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		doProxyTask(req, resp);
	}
	/**
	 * proxy動作をさせます。
	 * @param request
	 * @param response
	 */
	private void doProxyTask(HttpServletRequest request, HttpServletResponse response) {
		try {
			String requestURI = request.getRequestURI();
			if(requestURI.endsWith(".mp4")) {
				// 通常のmp4をそのまま応答
				manager2.accessMediaData(request, response);
			}
			else if(requestURI.endsWith(".m4")) {
				// mp4の動画タグをoffにしたバージョン
				manager3.accessMediaData(request, response);
			}
			else if(requestURI.endsWith(".m4a")) {
				// mp4の動画タグを削除したバージョン
				manager4.accessMediaData(request, response);
			}
			else if(requestURI.endsWith(".flv")) {
				// flvにコンテナ変換したバージョン
			}
			else if(requestURI.endsWith(".ts")) {
				// mpegtsにコンテナ変換したバージョン
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			// 例外がでた場合はとりあえずNO CONTENTとしておく。
			response.setStatus(HttpServletResponse.SC_NO_CONTENT);
			return;
		}
	}
}
