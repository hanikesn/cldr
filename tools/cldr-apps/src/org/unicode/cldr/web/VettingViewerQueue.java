/**
 * 
 */
package org.unicode.cldr.web;

import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.LruMap;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.VettingViewer.UsersChoice;
import org.unicode.cldr.util.VettingViewer.VoteStatus;
import org.unicode.cldr.util.VoteResolver;
import org.unicode.cldr.util.VoteResolver.Organization;
import org.unicode.cldr.web.UserRegistry.User;

import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.util.ULocale;

/**
 * @author srl
 *
 */
public class VettingViewerQueue {
	
	public static final boolean DEBUG = false || CldrUtility.getProperty("TEST", false);
	
	public static CLDRLocale SUMMARY_LOCALE = CLDRLocale.getInstance(ULocale.forLanguageTag("und-x-summary"));
	
	static VettingViewerQueue instance = new VettingViewerQueue();
	
	/**
	 * Get the singleton instance of the queue
	 * @return
	 */
	public static VettingViewerQueue getInstance() { 
		//if(DEBUG) System.err.println("SUMMARY_LOCALE="+SUMMARY_LOCALE.toString());
		return instance;
	}
	
    static int gMax = -1;

    /**
     * Count the # of paths in this CLDRFile
     * @param file
     * @return
     */
    private static int pathCount(CLDRFile f)
    {
        int jj=0;
        for(@SuppressWarnings("unused") String s: f) {
            jj++;
        }
        return jj;
    }

    /**
     * Get the max expected items in a CLDRFile
     * @param f
     * @return
     */
    private synchronized int getMax(CLDRFile f) {
        if(gMax==-1) {
            gMax = pathCount(f);
        }
        return gMax;
    }

	/**
	 * A unique key for hashes
	 */
	private static final String KEY = VettingViewerQueue.class.getName();
    
	
	/**
	 * Status of a Task
	 * @author srl
	 *
	 */
    public enum Status {
    	/** Waiting on other users/tasks */
    	WAITING, 
    	/** Processing in progress */
    	PROCESSING, 
    	/** Contents are available */
    	READY, 
    	/** Stopped, due to some err */
    	STOPPED 
    };
    
    /**
     * What policy should be used when querying the queue?
     * @author srl
     *
     */
    public enum LoadingPolicy {
    	/** (Default) - start if not started */
    	START,
    	/** Don't start if not started. Just check. */
    	NOSTART,
    	/** Force a restart, ignoring old contents */
    	FORCERESTART,
    };
    
    private static class QueueEntry {
    	public Task currentTask=null;
    	public Map<CLDRLocale,StringBuffer> output = new TreeMap<CLDRLocale,StringBuffer>();
    }
    
    public static QueueEntry summaryEntry = null;
    
    private static final Object OnlyOneVetter=new Object(){}; // TODO: remove.
    
	public class Task extends SurveyThread.SurveyTask {
		
		
		public CLDRLocale locale;
		private QueueEntry entry;
		SurveyMain sm;
		VettingViewer<VoteResolver.Organization> vv;
		public int maxn;
		public int n=0;
		public boolean isSummary = false;
		public long start =-1;
		public long last;
		public long rem = -1;
		final Level usersLevel;
		final Organization usersOrg;
		String status = "(Waiting for other users)";
		public Status statusCode = Status.WAITING; // Need to start out as waiting. 
		void setStatus(String status) {
			this.status = status;
		}
		public float progress() {
			if(maxn<=0) return (float)0.0;
			return ((float)n)/((float)maxn);
		}
		StringBuffer aBuffer = new StringBuffer();
		private String baseUrl;
		public Task(QueueEntry entry, CLDRLocale locale, SurveyMain sm, String baseUrl, Level usersLevel, VoteResolver.Organization usersOrg) {
			super("VettingTask:"+locale.toString());
			if(DEBUG) System.err.println("Creating task " + locale.toString());
			
			int baseMax = getMax(sm.getBaselineFile());
			if(locale.toString().length()==0) {
				isSummary=true;
				maxn=0;
				// use the hack set
				for(CLDRLocale l : sm.getLocalesSet()) {
					if(VettingViewer.HackIncludeLocalesWithVotes.is(l.toString())) {
						maxn += baseMax;
					}
				}
			} else {
				maxn = baseMax;
			}
			this.locale = locale;
			this.entry = entry;
			this.sm = sm;
			this.baseUrl = baseUrl;
			this.usersLevel = usersLevel; // Level.get(ctx.getEffectiveCoverageLevel());
			this.usersOrg = usersOrg; // VoteResolver.Organization.fromString(ctx.session.user.voterOrg());
		}

		@Override
		public void run() throws Throwable {
			statusCode = Status.WAITING;
			final CLDRProgressTask progress = openProgress("vv:"+locale,maxn+100);
			
			VettingViewer<VoteResolver.Organization> vv = null;
			
			if(DEBUG) System.err.println("Starting up vv task:"+locale);
			
			try {
				status="Waiting...";
				progress.update("Waiting...");
				synchronized(OnlyOneVetter) {
					if(!running()) { 
						status="Stopped on request.";
						statusCode=Status.STOPPED;  
						return;
					}
					status="Beginning Process, Calculating";
		            
		            vv = new VettingViewer<VoteResolver.Organization>(
		                    sm.getSupplementalDataInfo(), sm.getSTFactory(), sm.getOldFactory(),
		                    getUsersChoice(sm), "CLDR "+sm.getOldVersion(), "Winning "+sm.getNewVersion());
		            vv.setBaseUrl(baseUrl);
					progress.update("Got VettingViewer");
					statusCode = Status.PROCESSING;
					start = System.currentTimeMillis();
					last=start;
					n=0;
					vv.setProgressCallback(new VettingViewer.ProgressCallback(){
						public String setRemStr(long now) {
							double per = (double)(now-start)/(double)n;
							rem = (long)((maxn-n)*per);
							String remStr = ElapsedTimer.elapsedTime(now,now+rem) + " " + /*"("+rem+"/"+per+") "+*/"remaining";
							if(rem <= 1500) {
								remStr = "Finishing...";
							}
							setStatus(remStr);
							return remStr;
						}
						public void nudge() { 
							if(!running()) {
								throw new RuntimeException("Not Running- stop now.");
							}
							long now = System.currentTimeMillis();
							n++;
							//        System.err.println("Nudged: " + n);
							if(n>(maxn-5)) {
								maxn=n+10;
								if(!isSummary && n>gMax) {
									gMax=n;
								}
							}

							if((now-last)>1200) {
								last=now;
								//								StringBuffer bar = SurveyProgressManager.appendProgressBar(new StringBuffer(),n,ourmax);
								//								String remStr="";
								if(n>500) {
									progress.update(n,setRemStr(now));
								} else {
									progress.update(n);
								}
								//								try {
								//									mout.println("<script type=\"text/javascript\">document.getElementById('LoadingBar').innerHTML=\""+bar+ " ("+n+" items loaded"  + remStr + ")" + "\";</script>");
								//									mout.flush();
								//								} catch (java.io.IOException e) {
								//									System.err.println("Nudge: got IOException  " + e.toString() + " after " + n);
								//									throw new RuntimeException(e); // stop processing
								//								}
							}
						}
						public void done() { progress.update("Done!"); }
					});
					
					EnumSet <VettingViewer.Choice> choiceSet = EnumSet.allOf(VettingViewer.Choice.class);

					if(locale.toString().length()>0) {
						vv.generateHtmlErrorTables(aBuffer, choiceSet, locale.getBaseName(), usersOrg, usersLevel, true);
					} else {
						if(DEBUG) System.err.println("Starting summary gen..");
						vv.generateSummaryHtmlErrorTables(aBuffer, choiceSet, VettingViewer.HackIncludeLocalesWithVotes);
					}
					if(running()) {
						aBuffer.append("<hr/>"+PRE+"Processing time: "+ElapsedTimer.elapsedTime(start)+POST );
						entry.output.put(locale, aBuffer);
					}
				}
			} catch (RuntimeException re) {
				// We're done.
			} finally {
				if(progress!=null) progress.close();
				vv=null; // release vv
			}
		}

		public String status() {
			StringBuffer bar = SurveyProgressManager.appendProgressBar(new StringBuffer(),n,maxn);
			return status + bar;
		}

	}
	
	private static final String PRE = "<DIV class='pager'>";
	private static final String POST = "</DIV>";
	
	/**
	 * Same as getVettingViewerOutput  except that the status message, if present, will be written to the output
	 * @see #getVettingViewerOutput(WebContext, CookieSession, CLDRLocale, Status[], LoadingPolicy, Appendable)
	 * @param ctx
	 * @param sess
	 * @param locale
	 * @param status
	 * @param forceRestart
	 * @param output
	 * @throws IOException
	 */
	public void writeVettingViewerOutput(WebContext ctx, CookieSession sess, CLDRLocale locale, Status[] status, LoadingPolicy forceRestart, Appendable output) throws IOException {
	    String str = getVettingViewerOutput(ctx,sess,locale,status,forceRestart,output);
	    if(str!=null) {
	        output.append(str);
	    }
	}
        
        public void writeVettingViewerOutput(CLDRLocale locale, StringBuffer aBuffer, WebContext ctx, CookieSession sess) {
		String baseUrl = "http://example.com";
		Level usersLevel;
		Organization usersOrg;
		if(ctx!=null) {
			baseUrl = ctx.base();
			usersLevel =  Level.get(ctx.getEffectiveCoverageLevel(ctx.getLocale().toString()));
		} else {
			baseUrl = (String)sess.get("BASE_URL");
			String levelString = sess.settings().get(SurveyMain.PREF_COVLEV, WebContext.PREF_COVLEV_LIST[0]);;
			usersLevel = Level.get(levelString);
		}
		usersOrg = VoteResolver.Organization.fromString(sess.user.voterOrg());
                
                writeVettingViewerOutput(locale, baseUrl, aBuffer, usersOrg, usersLevel);
        }
        
        public void writeVettingViewerOutput(CLDRLocale locale, String baseUrl, StringBuffer aBuffer, VoteResolver.Organization usersOrg, Level usersLevel) {
            SurveyMain sm = CookieSession.sm;
	    VettingViewer            vv = new VettingViewer<VoteResolver.Organization>(
		                    sm.getSupplementalDataInfo(), sm.getSTFactory(), sm.getOldFactory(),
		                    getUsersChoice(sm), "CLDR "+sm.getOldVersion(), "Winning "+sm.getNewVersion());
		            vv.setBaseUrl(baseUrl);
					//progress.update("Got VettingViewer");
					//statusCode = Status.PROCESSING;
					final long start = System.currentTimeMillis();
					
					EnumSet <VettingViewer.Choice> choiceSet = EnumSet.allOf(VettingViewer.Choice.class);

					if(locale != SUMMARY_LOCALE) {
						vv.generateHtmlErrorTables(aBuffer, choiceSet, locale.getBaseName(), usersOrg, usersLevel, true);
					} else {
						if(DEBUG) System.err.println("Starting summary gen..");
						vv.generateSummaryHtmlErrorTables(aBuffer, choiceSet, VettingViewer.HackIncludeLocalesWithVotes);
					}
					/*if(running()) {
						aBuffer.append("<hr/>"+PRE+"Processing time: "+ElapsedTimer.elapsedTime(start)+POST );
						entry.output.put(locale, aBuffer);
					}*/
        }
	
	/**
	 * Return the status of the vetting viewer output request. 
	 * If a different locale is requested, the previous request will be cancelled.
	 * @param ctx
	 * @param locale
	 * @param output if there is output, it will be written here. Or not, if it's null
	 * @return status message
	 * @throws IOException 
	 */
	public synchronized String getVettingViewerOutput(WebContext ctx, CookieSession sess, CLDRLocale locale, Status[] status, 
			LoadingPolicy forceRestart, Appendable output) throws IOException {
		if(sess==null) sess = ctx.session;
		SurveyMain sm = sess.sm;
		boolean isSummary = locale.toString().length()==0;
		QueueEntry entry = null;
		if(!isSummary) {
			entry = getEntry(sess);
		} else {
			entry = getSummaryEntry();
		}
		if(status==null) status = new Status[1];
		if(forceRestart!=LoadingPolicy.FORCERESTART) {
			StringBuffer res = entry.output.get(locale);
			if(res != null) {
				status[0]=Status.READY;
				if(output!=null) {
				    output.append(res);
				}
				return null;
			}
		} else { /* force restart */
			stop(ctx, locale, entry);
	    	entry.output.remove(locale);
		}
		
		Task t = entry.currentTask;
		CLDRLocale didKill = null;
		
		if(t != null) { 
			String waiting = waitingString(t.sm.startupThread);
			if (t.locale.equals(locale)) {
				status[0]=Status.PROCESSING;
				if(t.running()) {
					// get progress from current thread
					status[0]=t.statusCode;
					if(status[0]!=Status.WAITING) waiting="";
					return PRE+"In Progress: " + waiting+ t.status()+POST;
				} else {
					return PRE+"Stopped (refresh if stuck) " + t.status()+POST;
				}
			} else if(forceRestart==LoadingPolicy.NOSTART){
				status[0]=Status.STOPPED;
				if(t.running()) {
					return PRE+" You have another locale being loaded: " + t.locale+POST;
				} else {
					return PRE+"Refresh if stuck."+POST;
				}
			} else {
				if(t.running()) {
					didKill = t.locale;
				}
				stop(ctx, t.locale, entry);
			}
		}
		
		if(forceRestart==LoadingPolicy.NOSTART){
			status[0]=Status.STOPPED;
			return PRE+"Not loading. Click the Refresh button to load."+POST;
		}
		
		String baseUrl = "http://example.com";
		Level usersLevel;
		Organization usersOrg;
		if(ctx!=null) {
			baseUrl = ctx.base();
			usersLevel =  Level.get(ctx.getEffectiveCoverageLevel(ctx.getLocale().toString()));
		} else {
			baseUrl = (String)sess.get("BASE_URL");
			String levelString = sess.settings().get(SurveyMain.PREF_COVLEV, WebContext.PREF_COVLEV_LIST[0]);;
			usersLevel = Level.get(levelString);
		}
		usersOrg = VoteResolver.Organization.fromString(sess.user.voterOrg());

		t = entry.currentTask = new Task(entry, locale, sm,baseUrl,usersLevel,usersOrg);
		sm.startupThread.addTask(entry.currentTask);
		
		status[0] = Status.PROCESSING;
		String killMsg = "";
		if(didKill!=null) {
			killMsg = " (Note: Stopped loading: "+didKill.toULocale().getDisplayName(SurveyMain.BASELINE_LOCALE)+")";
		}
		return PRE+"Started new task: " +waitingString(t.sm.startupThread)+ t.status()+"<hr/>"+killMsg+POST;
	}
	
    private String waitingString(SurveyThread startupThread) {
		int aheadOfMe=(totalUsersWaiting(startupThread));
		String waiting = (aheadOfMe>0)?(""+aheadOfMe+" users waiting - "):"";
		return waiting;
	}

	private void stop(WebContext ctx, CLDRLocale locale, QueueEntry entry) {
    	Task t = entry.currentTask;
    	if(t != null) {
    		if(t.running()) {
    			t.stop();
    		}
    		entry.currentTask=null;
    	}
	}

	private QueueEntry getEntry(CookieSession session) {
    	QueueEntry entry = (QueueEntry)session.get(KEY);
    	if(entry==null) {
    		entry = new QueueEntry();
    		session.put(KEY, entry);
    	}
    	return entry;
	}
	
	private synchronized QueueEntry getSummaryEntry() {
    	QueueEntry entry = summaryEntry;
    	if(summaryEntry==null) {
    		entry=summaryEntry = new QueueEntry();
    	}
    	return entry;
	}

    
    LruMap<CLDRLocale, BallotBox<UserRegistry.User>> ballotBoxes = new LruMap<CLDRLocale, BallotBox<User>>(8);
    BallotBox<UserRegistry.User> getBox(SurveyMain sm, CLDRLocale loc) {
        BallotBox<User> box = ballotBoxes.get(loc);
        if(box==null) {
            box = sm.getSTFactory().ballotBoxForLocale(loc);
            ballotBoxes.put(loc, box);
        }
        return box;
    }
    private UsersChoice<VoteResolver.Organization> getUsersChoice(final SurveyMain sm) {
        return new STUsersChoice(sm);
    }
    
    private class STUsersChoice implements UsersChoice<VoteResolver.Organization> {
        private final SurveyMain sm;
        STUsersChoice(final SurveyMain msm) {
            this.sm = msm;
        }
    	final Map<CLDRLocale,DataTester> testMap = new HashMap<CLDRLocale,DataTester>();
            private DataTester getTester(CLDRLocale loc) {
            	DataTester tester = testMap.get(loc);
            	if(tester==null) {
                    BallotBox<User> ballotBox = getBox(sm,loc);
            		//tester = getTester(ballotBox);
            		testMap.put(loc, tester);
            	}
            	return tester;
            }
        	
        	@Override
            public String getWinningValueForUsersOrganization(
                    CLDRFile cldrFile, String path, VoteResolver.Organization user) {
                CLDRLocale loc = CLDRLocale.getInstance(cldrFile.getLocaleID());
                BallotBox<User> ballotBox = getBox(sm,loc);
                return ballotBox.getResolver(path).getOrgVote(user);
            }
            
            @Override
            public VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, VoteResolver.Organization orgOfUser) {
                CLDRLocale loc = CLDRLocale.getInstance(cldrFile.getLocaleID());
                BallotBox<User> ballotBox = getBox(sm,loc);
                return ballotBox.getResolver(path).getStatusForOrganization(orgOfUser);
            }
    }
    
    private static int totalUsersWaiting(SurveyThread st) {
    	return (st.tasksRemaining(Task.class));
    }
} 
