package org.zkoss.fiddle.composer;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.zkoss.fiddle.FiddleConstant;
import org.zkoss.fiddle.component.renderer.ISourceTabRenderer;
import org.zkoss.fiddle.component.renderer.SourceTabRendererFactory;
import org.zkoss.fiddle.composer.TopNavigationComposer.State;
import org.zkoss.fiddle.composer.event.FiddleEvents;
import org.zkoss.fiddle.composer.event.InsertResourceEvent;
import org.zkoss.fiddle.composer.event.ResourceChangedEvent.Type;
import org.zkoss.fiddle.composer.event.SaveCaseEvent;
import org.zkoss.fiddle.composer.event.URLChangeEvent;
import org.zkoss.fiddle.composer.eventqueue.FiddleEventListener;
import org.zkoss.fiddle.composer.eventqueue.FiddleEventQueues;
import org.zkoss.fiddle.composer.eventqueue.impl.FiddleBrowserStateEventQueue;
import org.zkoss.fiddle.composer.eventqueue.impl.FiddleSourceEventQueue;
import org.zkoss.fiddle.composer.eventqueue.impl.FiddleTopNavigationEventQueue;
import org.zkoss.fiddle.composer.viewmodel.CaseModel;
import org.zkoss.fiddle.composer.viewmodel.URLData;
import org.zkoss.fiddle.dao.api.ICaseRatingdDao;
import org.zkoss.fiddle.dao.api.ICaseRecordDao;
import org.zkoss.fiddle.dao.api.ICaseTagDao;
import org.zkoss.fiddle.dao.api.ITagDao;
import org.zkoss.fiddle.fiddletabs.Fiddletabs;
import org.zkoss.fiddle.manager.CaseManager;
import org.zkoss.fiddle.manager.FiddleSandboxManager;
import org.zkoss.fiddle.model.Case;
import org.zkoss.fiddle.model.CaseRating;
import org.zkoss.fiddle.model.CaseRecord;
import org.zkoss.fiddle.model.Resource;
import org.zkoss.fiddle.model.Tag;
import org.zkoss.fiddle.model.api.ICase;
import org.zkoss.fiddle.notification.Notification;
import org.zkoss.fiddle.util.BrowserStateUtil;
import org.zkoss.fiddle.util.CookieUtil;
import org.zkoss.fiddle.util.NotificationUtil;
import org.zkoss.fiddle.util.SEOUtils;
import org.zkoss.fiddle.util.TagUtil;
import org.zkoss.fiddle.util.UserUtil;
import org.zkoss.fiddle.visualmodel.CaseRequest;
import org.zkoss.fiddle.visualmodel.FiddleSandbox;
import org.zkoss.fiddle.visualmodel.RatingAmount;
import org.zkoss.fiddle.visualmodel.UserVO;
import org.zkoss.rating.Rating;
import org.zkoss.rating.event.RatingEvent;
import org.zkoss.service.login.IReadonlyLoginService;
import org.zkoss.service.login.IUser;
import org.zkoss.social.facebook.event.LikeEvent;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Desktop;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zkplus.spring.SpringUtil;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.Div;
import org.zkoss.zul.Hlayout;
import org.zkoss.zul.Label;
import org.zkoss.zul.Tab;
import org.zkoss.zul.Tabpanels;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.Toolbarbutton;
import org.zkoss.zul.Window;

import ork.zkoss.fiddle.hyperlink.Hyperlink;

public class SourceCodeEditorComposer extends GenericForwardComposer {

	/**
	 *
	 */
	private static final long serialVersionUID = -5940380002871513285L;

	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger
			.getLogger(SourceCodeEditorComposer.class);

	/**
	 * case management model.
	 */
	private CaseModel caseModel;

	private Fiddletabs sourcetabs;

	private Tabpanels sourcetabpanels;

	private Textbox caseTitle;

	private Div caseToolbar;

	/* author start */

	private Hyperlink authorLink;

	private Textbox authorName;

	private Div authorControl;

	private Hyperlink loginedAuthorName;

	private Hyperlink logoffBtn;

	private Hyperlink loginBtn;

	private Window loginWin;

	/* author end */

	/*login start*/

	private Textbox loginWin$account;

	private Textbox loginWin$password;

	/* login end */

	private Toolbarbutton download;

	private Label poserIp;

	/* for tags */
	private Hlayout tagContainer;

	private Label tagEmpty;

	private Textbox tagInput;

	private Hlayout editTag;

	private Hlayout viewTag;

	private String lastVal;

	private Checkbox cbSaveTag;

	/* for rating */

	private Rating caseRating;

	/* for notifications */
	private Div notifications;

	public void doAfterCompose(Component comp) throws Exception {
		super.doAfterCompose(comp);
		Execution exec = Executions.getCurrent();
		Case _case = (Case) exec.getAttribute(FiddleConstant.REQUEST_ATTR_CASE);

		updateNotifications();

		caseModel = prepareCaseModel(_case);
		updateCaseView(caseModel);
		updateTopNavigation();

		initEventQueue();

		initTagEditor();

		initSEOHandler(caseModel, desktop);

		// if direct view , we handle it here.
		initDirectlyView();

		initAuthor();


	}


	private void updateTopNavigation(){
		if(caseModel.isStartWithNewCase()){
			FiddleTopNavigationEventQueue.lookup().fireStateChange(State.New);
		}else{
			FiddleTopNavigationEventQueue.lookup().fireStateChange(State.Saved);
		}
	}
	/**
	 *
	 */
	private void updateNotifications() {
		List<String> list = NotificationUtil.getNotifications(Sessions
				.getCurrent());

		notifications.getChildren().clear();

		for (String message : list) {
			Notification notification = new Notification(message);
			notification.setSclass("fiddle-nofication");
			notifications.appendChild(notification);
		}
		notifications.invalidate();
		NotificationUtil.clearNotifications(Sessions.getCurrent());
	}

	private CaseModel prepareCaseModel(Case _case) {
		if (!isTryCase()) {
			return new CaseModel(_case, false, null);
		} else {
			String zulData = (String) Executions.getCurrent().getParameter(
					"zulData");
			String version = (String) Executions.getCurrent().getParameter(
					"zkver");
			Events.echoEvent(new Event("onShowTryCase", self, version));
			return new CaseModel(_case, true, zulData);
		}
	}


	private boolean isTryCase() {
		Boolean tryCase = (Boolean) requestScope
				.get(FiddleConstant.REQUEST_ATTR_TRY_CASE);
		return tryCase != null && tryCase;
	}


	private void initAuthor(){
		//only fill the author field at begining.
		String cookieAuthor = CookieUtil.getCookie(FiddleConstant.COOKIE_ATTR_AUTHOR_NAME);
		boolean emptyAuthor = (cookieAuthor == null || "".equals(cookieAuthor));
		authorName.setValue((emptyAuthor) ? "guest" : cookieAuthor);

		authorLink.addEventListener("onClick", new EventListener() {
			public void onEvent(Event event) throws Exception {
				if(!caseModel.isStartWithNewCase()){
					UserVO userVO = new UserVO(caseModel.getCurrentCase());
					BrowserStateUtil.go(userVO);
				}
			}
		});

		if(!UserUtil.isLogin(Sessions.getCurrent()) && !emptyAuthor){
			loginWin$account.setValue(cookieAuthor);
		}
	}

	private void initEventQueue() {

		final FiddleSourceEventQueue sourceQueue = FiddleSourceEventQueue
				.lookup();
		sourceQueue.subscribeResourceCreated(
			new FiddleEventListener<InsertResourceEvent>(
					InsertResourceEvent.class,self) {

				public void onFiddleEvent(InsertResourceEvent event)
						throws Exception {
					InsertResourceEvent insertEvent = (InsertResourceEvent) event;
					Resource resource = insertEvent.getResource();

					caseModel.addResource(resource);
					ISourceTabRenderer render = SourceTabRendererFactory
							.getRenderer(resource.getType());
					render.appendSourceTab(sourcetabs, sourcetabpanels,
							resource);
					sourceQueue.fireResourceChanged(resource,Type.Modified);
					((Tab) sourcetabs.getLastChild()).setSelected(true);
				}
			});
		sourceQueue.subscribeResourceSaved(
			new FiddleEventListener<SaveCaseEvent>( SaveCaseEvent.class,self) {
				public void onFiddleEvent(SaveCaseEvent event)
						throws Exception {
					SaveCaseEvent saveEvt = (SaveCaseEvent) event;
					CaseManager caseManager = (CaseManager) SpringUtil
							.getBean("caseManager");

					String title = caseTitle.getValue().trim();
					String ip = Executions.getCurrent().getRemoteAddr();

					String autherName = authorName.getText();
					autherName.replaceAll("[^a-zA-Z ]","");
					boolean isGuest = true;

					if(UserUtil.isLogin(Sessions.getCurrent())){
						IUser user = UserUtil.getLoginUser(Sessions.getCurrent());
						autherName = user.getName();
						isGuest = false;
					}
					Case saved = caseManager.saveCase(
							caseModel.getCurrentCase(),
							caseModel.getResources(),
							title,
							autherName,	isGuest,
							saveEvt.isFork(), ip, cbSaveTag.isChecked());

					if (saved != null) {
						List<String> notifications = NotificationUtil
								.getNotifications(Sessions.getCurrent());

						if (caseModel.isStartWithNewCase()) {
							notifications
									.add("You have saved a new sample.");
						} else if (saveEvt.isFork()) {
							notifications.add("You have forked the sample. ");
						} else {
							notifications.add("You have updated the sample. ");
						}
						NotificationUtil.updateNotifications(
								Sessions.getCurrent(), notifications);

						BrowserStateUtil.go(saved);
					}
				}
			});

		/**
		 * browser state , for chrome and firefox only
		 */
		FiddleBrowserStateEventQueue queue = FiddleBrowserStateEventQueue.lookup();
		queue.subscribe(new FiddleEventListener<URLChangeEvent>(
				URLChangeEvent.class,self) {

			public void onFiddleEvent(URLChangeEvent evt) throws Exception {
				// only work when updated to a case view.
				URLData data = (URLData) evt.getData();

				if (data == null ){
					throw new IllegalStateException("not expected type");
				}else if(FiddleConstant.URL_DATA_CASE_VIEW.equals(data.getType())) {
					Case _case = (Case) data.getData();
					caseModel.setCase(_case);
					updateCaseView(caseModel);
					updateTopNavigation();
					updateNotifications();
					EventQueues.lookup(FiddleEventQueues.LeftRefresh).publish(
							new Event(FiddleEvents.ON_LEFT_REFRESH, null));
				}
			}
		});
	}
	private void initTagEditor(){
		tagInput.addEventListener("onOK", new EventListener() {
			public void onEvent(Event event) throws Exception {
				performUpdateTag();
			}
		});
		tagInput.addEventListener("onCancel", new EventListener() {

			public void onEvent(Event event) throws Exception {
				tagInput.setValue(lastVal);
				setTagEditable(false);
				event.stopPropagation();
			}
		});
	}

	private void initDirectlyView() {
		// @see FiddleDispatcherFilter for those use this directly
		CaseRequest viewRequestParam = (CaseRequest) requestScope
				.get(FiddleConstant.REQUEST_ATTR_RUN_VIEW);
		if (viewRequestParam != null) {
			runDirectlyView(viewRequestParam);
		}
	}

	private void updateLoginState(){
		caseRating.setReadOnly(! UserUtil.isLogin(Sessions.getCurrent()));

		if(UserUtil.isLogin(Sessions.getCurrent())){
			if(!caseModel.isStartWithNewCase())
				updateCaseRating(caseModel.getCurrentCase());

			IUser user = UserUtil.getLoginUser(Sessions.getCurrent());
			loginedAuthorName.setLabel(user.getName());
			loginedAuthorName.setVisible(true);

			authorName.setValue(user.getName());
			authorName.setVisible(false);
			logoffBtn.setVisible(true);
			loginBtn.setVisible(false);
		}else{

			caseRating.setRated(false);

			authorName.setVisible(true);
			loginedAuthorName.setVisible(false);
			logoffBtn.setVisible(false);
			loginBtn.setVisible(true);
		}

	}

	private void updateCaseView(CaseModel caseModel) {
		// FiddleBrowserStateEventQueue

		boolean newCase = caseModel.isStartWithNewCase();

		authorLink.setVisible(false);
		if (!newCase) {
			Case thecase = caseModel.getCurrentCase();
			caseTitle.setValue(thecase.getTitle());
			download.setHref(caseModel.getDownloadLink());

			//The invalidate is fixing the issue for #147,
			//since we use client event to switch the view ,
			//so I can't switch it back in server side ,
			//it's better to invalidate it directly. by Tony.

			caseToolbar.invalidate();
			caseToolbar.setVisible(true);
			poserIp.setValue(thecase.getPosterIP());
			updateTagEditor(thecase);
			updateCaseRating(thecase);

			authorLink.setHref(UserUtil.getUserView(thecase));
			authorLink.setVisible(true);
			authorLink.setSclass(thecase.isGuest()? "guest-user author":"author");
			authorLink.setLabel(thecase.getAuthorName());

			authorControl.setSclass("author-saved");
		}else{
			authorControl.setSclass("author-new");
		}
		updateLoginState();

		sourcetabs.getChildren().clear();
		sourcetabpanels.getChildren().clear();

		final FiddleSourceEventQueue sourceQueue = FiddleSourceEventQueue
				.lookup();
		for (Resource resource : caseModel.getResources()) {
			ISourceTabRenderer render = SourceTabRendererFactory
					.getRenderer(resource.getType());
			render.appendSourceTab(sourcetabs, sourcetabpanels, resource);
			if (newCase) {
				// Notify content to do some processing,since we use desktop
				// scope eventQueue,it will not be a performance issue.
				sourceQueue.fireResourceChanged(resource, Type.Created);
			}
		}
	}

	public void onRating$caseRating(RatingEvent evt){
		if(UserUtil.isLogin(Sessions.getCurrent()) && ! caseModel.isStartWithNewCase()){
			IUser user = UserUtil.getLoginUser(Sessions.getCurrent());
			CaseManager caseManager = (CaseManager) SpringUtil.getBean("caseManager");
			RatingAmount ratingResultAmount = caseManager.rankCase(caseModel.getCurrentCase(), user.getName(), evt.getValue());
			caseRating.setValue((int) ratingResultAmount.getAmount());
			caseRating.setRatedvalue(evt.getValue());
		}
	}

	private void updateCaseRating(final Case thecase){

		caseRating.setReadOnly(! UserUtil.isLogin(Sessions.getCurrent()));

		ICaseRecordDao caseRecordDao = (ICaseRecordDao) SpringUtil.getBean("caseRecordDao");
		CaseRecord record = caseRecordDao.get(CaseRecord.Type.Rating,thecase.getId());
		caseRating.setRatedvalue(0);
		caseRating.setRated(false);
		if(record == null){
			caseRating.setValue(0);
		}else{
			caseRating.setValue(record.getAmount().intValue());

			if(UserUtil.isLogin(Sessions.getCurrent())){
				IUser user = UserUtil.getLoginUser(Sessions.getCurrent());
				ICaseRatingdDao caseRatingDao = (ICaseRatingdDao) SpringUtil.getBean("caseRatingDao");
				CaseRating userRating = caseRatingDao.findBy(thecase, user.getName());
				if(userRating != null){
					caseRating.setRatedvalue(userRating.getAmount().intValue());
				}
			}

		}

	}

	private void updateTagEditor(final Case pCase) {
		lastVal = null;
		ICaseTagDao caseTagDao = (ICaseTagDao) SpringUtil.getBean("caseTagDao");
		updateTags(caseTagDao.findTagsBy(pCase));
	}

	private void updateTags(List<Tag> list) {
		tagContainer.getChildren().clear();
		if (list.size() == 0) {
			tagInput.setValue("");
			tagEmpty.setVisible(true);
			cbSaveTag.setVisible(false);
		} else {
			StringBuffer sb = new StringBuffer();
			for (final Tag tag : list) {
				Hyperlink lbl = new Hyperlink(tag.getName());
				final String tagurl = TagUtil.getViewURL(tag);
				lbl.setHref(tagurl);
				lbl.addEventListener("onClick", new EventListener() {
					public void onEvent(Event event) throws Exception {
						//FIXME this title is weird
						BrowserStateUtil.go(tag);
					}
				});
				lbl.setSclass("case-tag");
				sb.append(tag.getName() + ",");
				tagContainer.appendChild(lbl);
			}
			if (sb.length() != 0) {
				sb.deleteCharAt(sb.length() - 1);
			}
			tagInput.setValue(sb.toString());
			lastVal = sb.toString();
			tagEmpty.setVisible(false);
			cbSaveTag.setVisible(true);
		}
	}
	private void setTagEditable(boolean bool) {

		// 2011/6/27:TonyQ
		// set visible twice for forcing smart update
		// sicne we set visible in client , so the visible state didn't sync
		// with server,
		// we need to make sure the server will really send the smartUpdate
		// messages. ;)
		editTag.setVisible(!bool);
		editTag.setVisible(bool); // actually we want editTag visible false

		viewTag.setVisible(bool);
		viewTag.setVisible(!bool); // actually we want viewTag visible true
	}

	private void performUpdateTag() {

		String val = tagInput.getValue();

		boolean valueChange = (lastVal == null || !val.equals(lastVal));
		// Do nothing if it didn't change
		if (valueChange) {
			ITagDao tagDao = (ITagDao) SpringUtil.getBean("tagDao");

			List<Tag> list = "".equals(val.trim()) ? new ArrayList<Tag>()
					: tagDao.prepareTags(val.split("[ ]*,[ ]*"));
			ICaseTagDao caseTagDao = (ICaseTagDao) SpringUtil
					.getBean("caseTagDao");
			caseTagDao.replaceTags(caseModel.getCurrentCase(), list);

			EventQueues.lookup(FiddleEventQueues.Tag).publish(
					new Event(FiddleEvents.ON_TAG_UPDATE, null));

			updateTags(list);
		}

		setTagEditable(false);
	}


	private void runDirectlyView(CaseRequest viewRequestParam) {

		FiddleSandbox sandbox = viewRequestParam.getFiddleSandbox();
		if (sandbox != null) { // inst can't be null
			// use echo event to find a good timing
			Events.echoEvent(new Event(FiddleEvents.ON_SHOW_RESULT, self,
					viewRequestParam));
		} else {
			alert("Can't find sandbox from specific version ");
		}
	}

	public void onChange$authorName(InputEvent evt){
		String name = evt.getValue().replaceAll("[\\\\/\\.&$#@:!?]","");
		String newname = name.substring(0,name.length()>20?20:name.length());
		authorName.setValue(newname);
		CookieUtil.setCookie(FiddleConstant.COOKIE_ATTR_AUTHOR_NAME, newname, CookieUtil.AGE_ONE_YEAR);

	}

	//TODO review this and remove it.
	public void onLike$fblike(LikeEvent evt) {
		ICaseRecordDao manager = (ICaseRecordDao) SpringUtil
				.getBean("caseRecordDao");
		ICase $case = caseModel.getCurrentCase();
		if (evt.isLiked()) {
			if (logger.isDebugEnabled()) {
				logger.debug($case.getToken() + ":" + $case.getVersion()
						+ ":like");
			}
			manager.increase(CaseRecord.Type.Like, $case);
		} else {
			if (logger.isDebugEnabled()) {
				logger.debug($case.getToken() + ":" + $case.getVersion()
						+ ":unlike");
			}
			manager.decrease(CaseRecord.Type.Like, $case.getId());
		}
	}

	public void onShowResult(Event e) {
		CaseRequest viewRequestParam = (CaseRequest) e.getData();
		if (viewRequestParam != null) {
			FiddleSourceEventQueue.lookup().fireShowResult(
					caseModel.getCurrentCase(),
					viewRequestParam.getFiddleSandbox());
		}
	}

	public void onShowTryCase(Event e) {

		FiddleSandboxManager manager = (FiddleSandboxManager) SpringUtil
				.getBean("sandboxManager");

		FiddleSandbox sandbox = null;
		String version = (String) e.getData();
		if (version != null) {
			sandbox = manager.getFiddleSandboxByVersion(version);
		} else {
			sandbox = manager.getFiddleSandboxForLastestVersion();
		}

		if (sandbox == null) {
			if (version == null) {
				alert("Currently no any available sandbox.");
			} else {
				alert("Currently no any available sandbox for ZK version["
						+ version + "].");
			}
			return;
		}

		caseModel.ShowResult(sandbox);
	}

	public void onClick$loginBtn(Event evt){
		loginWin.doOverlapped();
		if(!"".equals(authorName.getValue())
		   && !"guest".equals(authorName.getValue())
		){
			loginWin$account.setValue(authorName.getValue());
		}
		loginWin$account.focus();
	}

	public void onClick$logoffBtn(Event evt){
		UserUtil.logout(Sessions.getCurrent());
		this.updateLoginState();
	}

	public void onCancel$loginWin(Event evt){
		loginWin.setMinimized(true);
	}

	public void onOK$loginWin(Event evt){
		if(loginWin$account.isValid() && loginWin$password.isValid()){
			IReadonlyLoginService loginService = (IReadonlyLoginService) SpringUtil.getBean("loginManager");

			IUser user = loginService.verifyUser(loginWin$account.getValue(), loginWin$password.getValue());

			if (user != null) {
				UserUtil.login(Sessions.getCurrent(), user);
				loginWin.setMinimized(true);
				this.updateLoginState();
			}else{
				alert("account or password incorrect.");
			}
		}
	}

	public void onAdd$sourcetabs(Event e) {
		try {
			// the reason for not using auto wire is that the insertWin is
			// included when fulfill.
			((Window) self.getFellow("insertWin")).doOverlapped();
		} catch (Exception e1) {
			logger.error("onAdd$sourcetabs(Event) - e=" + e, e1);
		}
	}

	private static void initSEOHandler(CaseModel model, Desktop desktop) {
		SEOUtils.render(desktop, model.getCurrentCase());
		SEOUtils.render(desktop, model.getResources());
	}
}
