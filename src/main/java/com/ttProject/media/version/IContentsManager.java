package com.ttProject.media.version;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * コンテンツの応答のベース
 * @author taktod
 */
public interface IContentsManager {
	// 解析する。
	public void analyze() throws Exception;
	// 応答を返す
	public void accessMediaData(HttpServletRequest request, HttpServletResponse response) throws Exception;
}
