package com.ttProject.media.test;

import org.apache.log4j.Logger;
import org.junit.Test;

/**
 * try-catch-finallyの動作テスト
 * @author taktod
 *
 */
public class TryCatchTest {
	private Logger logger = Logger.getLogger(TryCatchTest.class);
	@Test
	public void test() {
		try {
			logger.info("start");
			return;
		}
		catch(Exception e) {
			logger.error(e);
		}
		finally{
			logger.info("finally");
		}
	}
}
