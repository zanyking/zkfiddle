package org.zkoss.fiddle.util;

import org.zkoss.fiddle.FiddleConstant;
import org.zkoss.fiddle.composer.eventqueue.impl.FiddleBrowserStateEventQueue;
import org.zkoss.fiddle.composer.viewmodel.URLData;
import org.zkoss.json.JSONValue;
import org.zkoss.zk.au.out.AuScript;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;

/**
 * Util classes for PushState support, note this depends on the execution.
 *
 * @author tony
 *
 */
public class BrowserState {

	public static void go(String uri, String title, URLData data) {
		go(uri, title, true, data, true);
	}

	public static void go(String uri, String title, boolean addContext, URLData data) {
		go(uri, title, addContext, data, true);
	}

	public static void go(String uri, String title, boolean addContext, URLData data, boolean updateState) {

		Execution exec = Executions.getCurrent();
		Boolean init = (Boolean) exec.getDesktop().getAttribute(FiddleConstant.DESKTOP_ATTR_PUSH_STATE);
		if (init != null && init) {
			if (addContext) {
				uri = exec.getContextPath() + uri;
			}
			FiddleBrowserStateEventQueue.lookup().fireStateChange(uri, data);

			if (updateState) {
				exec.addAuResponse(new AuScript(null, "History.pushed = true;History.pushState({}," + JSONValue.toJSONString(title) + ","
						+ JSONValue.toJSONString(uri) + ");"));
			}

		} else { //note  if send redirect , need not to consider addContext.
			exec.sendRedirect(uri);
		}
	}
}
