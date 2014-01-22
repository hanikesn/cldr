<%@page import="org.unicode.cldr.web.SurveyMain.Phase"%>
<%@page import="java.sql.SQLException"%>
<%@page import="org.unicode.cldr.util.VoteResolver.VoterInfo"%>
<%@page import="com.ibm.icu.util.VersionInfo"%>
<%@page
	import="org.tmatesoft.sqljet.core.internal.lang.SqlParser.commit_stmt_return"%>
<%@page import="java.util.Properties"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	import="org.unicode.cldr.web.*,org.unicode.cldr.util.*,java.io.*,java.util.Set,java.util.TreeSet"
	pageEncoding="UTF-8"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
	CLDRConfig cconfig = CLDRConfig.getInstance();
    File surveyHome = new File(SurveyMain.getSurveyHome());
    String theirVap = cconfig.getProperty("CLDR_VAP", null);
    String vap = request.getParameter("vap");
    File propsFile = new File(surveyHome,CLDRConfigImpl.CLDR_PROPERTIES);
    File adminHtml = new File(surveyHome, "admin.html");
    String clickHere = "<a href='"+request.getContextPath()+"/survey'>here</a>";
    Properties survProps = new Properties();
    java.io.FileInputStream is = new java.io.FileInputStream(propsFile);
    survProps.load(is);
    // progress.update("Loading configuration..");
    is.close();
    
    final boolean rawMaint = Boolean.getBoolean(survProps.getProperty("CLDR_MAINTENANCE", "false").trim().toLowerCase()) 
    			|| cconfig.getProperty("CLDR_MAINTENANCE", false);
    
    final String DOCLINK = "<a href='http://cldr.unicode.org/development'>cldr.unicode.org</a>";
	int errs=0;
%><%!static void writeProps(Properties survProps, File propsFile,
			HttpServletRequest request) throws IOException {
		File backup = new File(propsFile.getParentFile(), propsFile.getName()
				+ ".backup");
		backup.delete();
		propsFile.renameTo(backup);
		FileOutputStream os = new FileOutputStream(propsFile);
		survProps.store(os, "Auto generated by cldr-setup on "
				+ new java.util.Date() + " by " + request.getRemoteAddr());
		os.close();
	}%>
<%
	String problem = null;

if(!surveyHome.isDirectory()) {
	problem = ("SurveyHome is not a directory - " + surveyHome.getAbsolutePath());
} else {
    File maintFile = SurveyMain.getHelperFile();
    if(!maintFile.exists() && request!=null) {
       SurveyMain.writeHelperFile(request, maintFile);
    }
}
// From here on, we work with the config file manually.


if(theirVap==null) {
    	problem = ("SurveyTool does not yet have a password. Please attempty to view the main page such as "+clickHere+" and then try this page again.");
}
if(!theirVap.equals(survProps.getProperty("CLDR_VAP"))) {
	problem = ("<h2>VAP did not match in " + propsFile.getAbsolutePath()  + "-maybe you changed the password? <br> please #1 delete the file "+adminHtml.getAbsolutePath() +", <br>#2 restart the server and <br>#3 click " + clickHere + " to rebuild admin.html.</h2>");
}


if(problem != null) {
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR | SurveyTool Configurator | Error</title>
</head>
<body>
	<h1>SurveyTool Configurator | Error</h1>
	<p>
		<b>Error:</b><%=problem%></p>
	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>
<%
	return; 
	}


	if(!theirVap.equals(vap)) {
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR | SurveyTool Configurator | Login Failure</title>
</head>
<body>
	<h1>SurveyTool Configurator | Login Failure</h1>
	<p>
		<b>Error:</b>VAP password Incorrect
	</p>
	<form action="<%=request.getContextPath()+request.getServletPath()%>"
		METHOD="POST">
		<label>CLDR_VAP:<input name='vap' value=''></label><input
			type='submit' value='Submit'>
	</form>
	<hr>
	<p>If you got here via admin.html, try deleting that file and
		restarting the server.</p>
	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>
<%
	return;
	}


    if(!SurveyMain.isMaintenance() 
    		&& rawMaint ==false ) {
		if(request.getParameter("set_maint")!=null) {
%>

<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR | SurveyTool Configurator | Set Maintenance Mode</title>
</head>
<body>
	<h1>SurveyTool Configurator | Set Maintenance Mode</h1>

	<%
		{
		    survProps.put("CLDR_MAINTENANCE", "true");
		    writeProps(survProps,propsFile,request);
		    SurveyMain.busted("Restart into maint mode");
		}
	%>
	<h1>Now, restart the web server and then click the following
		button to configure the survey tool:</h1>
	<form action="<%=request.getContextPath()+request.getServletPath()%>"
		METHOD="POST">
		<input name='vap' type='hidden' value='<%=vap%>'> <input
			type='submit' value='Setup SurveyTool'>
	</form>


	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>

<%
	return;
		} else {
%>
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR | SurveyTool Not in Maintenance Mode</title>
</head>
<body>
	<h1>SurveyTool Configurator | SurveyTool Not in Maintenance Mode</h1>
	<ul>
	<!--
		<li><b>raw stuff</b>
			<ol>
				<li>propsFile: <%=propsFile%></li>
				<li>survProps empty: <%=survProps.isEmpty()%></li>
				<li>isSetup: <%=SurveyMain.isSetup%></li>
				<li>isConfigSetup: <%=SurveyMain.isConfigSetup%></li>
				<li>isBusted: <%=SurveyMain.isBusted()%></li>
				<li>rawMaint: <%=rawMaint%></li>
				<li>CLDR_MAINTENANCE: <%=survProps.getProperty("CLDR_MAINTENANCE","(not set)")%></li>
			</ol></li>
	-->
		<li>To reconfigure the SurveyTool, click the following button,
			then restart the web server:
			<form
				action="<%=request.getContextPath()+request.getServletPath()%>"
				METHOD="POST">
				<input name='vap' type='hidden' value='<%=vap%>'> <input
					name='set_maint' type='submit' value='Go into Maintenance Mode'>
			</form>
		</li>
		<li><b>Otherwise</b> to start with a fresh install, delete <%=surveyHome.getAbsolutePath()%>
			and then restart the survey tool.</li>
	</ul>
	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>
<%
	return;
		}
    }

if(request.getParameter("remove_maint")!=null) {
%>

<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<title>CLDR | SurveyTool Configurator | Return to Normal Mode</title>
</head>
<body>
	<h1>SurveyTool Configurator | Return to Normal Mode</h1>

	<%
		{
		    survProps.put("CLDR_MAINTENANCE", "false");
		    writeProps(survProps,propsFile,request);
		    SurveyMain.busted("Restart out of maint mode");
		}
	%>
	<h1>Now, restart the web server and then click this button to go
		to the SurveyTool:</h1>
	<form action="<%=request.getContextPath()%>/survey" METHOD="GET">
		<input type='submit' value='Return to the SurveyTool' />
	</form>

	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>

<%
	return;
}
%>

<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link href="surveytool.css" rel="stylesheet" type="text/css" />
<title>CLDR | SurveyTool Configurator</title>
<script src='js/cldr-setup.js'>
	
</script>
</head>
<body>
	<h1>SurveyTool Configurator</h1>

	<h2>Set Parameters</h2>
	<%
		{
			Set<String> s = new TreeSet<String>();
			for (org.unicode.cldr.web.SurveyMain.Phase p : org.unicode.cldr.web.SurveyMain.Phase
					.values()) {
				s.add("<tt class='codebox'>" + p.name().toUpperCase()
						+ "</tt>");
			}
			final String possiblePhases = "Possible phases: "
					+ com.ibm.icu.text.ListFormatter.getInstance()
							.format(s);

			String setupvars[] = {
					"CLDR_VAP",
					"This is the master password (the 'VAP') for the surveytool. You may leave this value as is or change it. (The admin.html file may become out of date if the password is changed. Delete it and it will be regenerated.)",
					"CLDR_TESTPW",
					"Test password for 'smoketest' like functionality. Allows anyone with password to create an account. May be left blank.",
					"CLDR_DIR",
					"If you already have CLDR's trunk (http://unicode.org/repos/cldr/trunk) checked out somewhere else, enter its path here. <br>If left as the default, it will be checked out automatically in a later step.",
					"CLDR_OLDVERSION",
					"Required: What is the previous released version of CLDR? (One possible value: "
							+ (VersionInfo
									.getInstance(CLDRFile.GEN_VERSION)
									.getMajor() - 1),
					"CLDR_NEWVERSION",
					"Required: What is the version of CLDR being surveyed for? (One possible value:  "
							+ CLDRFile.GEN_VERSION,
					"CLDR_PHASE",
					"Required: What phase is the SurveyTool in? A good phase for testing is: "
							+ org.unicode.cldr.web.SurveyMain.Phase.BETA
									.name() + " - " + possiblePhases,
					"CLDR_MESSAGE",
					"This message is only used when you wish the SurveyTool to be offline. Leave blank for normal startup",
					"CLDR_HEADER",
					"This message is displayed at the top of each SurveyTool page. It may be removed if desired.", };
			int which = 0;
			String value = null;
			String field = null;
			int whichF = -1;
			String whichT = request.getParameter("which");
			if (whichT != null) {
				which = Integer.parseInt(whichT);
				if (which < 0 || which > (setupvars.length)) {
					which = 0;
				}
			}
	%><form
		action='<%=request.getContextPath() + request.getServletPath()%>'
		method='POST'>
		<%
			if (which > 1) {
		%>
		<button value='<%=which - 2%>' name='which'>Save and Go Back</button>
		<hr>
		<%
			}
				for (int i = 0; i < setupvars.length; i += 2) {
					String param = request.getParameter(setupvars[i]);
					if (param != null) {
						value = WebContext.decodeFieldString(param);
						if (value != null) {
							value = value.trim();
						}
						whichF = i;
						field = setupvars[i];
					}
				}
				if (value != null && field != null) {
					String valueErr = null;

					if (field.equals("CLDR_VAP")) {
						if (value == null || value.trim().length() == 0) {
							valueErr = "This parameter may not be left blank.";
						}
					} else if (field.equals("CLDR_OLDVERSION")
							|| field.equals("CLDR_NEWVERSION")) {
						if (value == null || value.trim().length() == 0) {
							valueErr = "Version number must not be null.";
							value = "";
						} else {
							try {
								final String asString = com.ibm.icu.util.VersionInfo
										.getInstance(value).toString();
								if (asString == null) {
									valueErr = "Version number is not valid";
								} else {
									// value = asString; //  25 -> 25.0.0.0 - not good.
								}
							} catch (IllegalArgumentException iae) {
								valueErr = iae.getMessage();
							}
						}
					} else if (field.equals("CLDR_PHASE")) {
						try {
							org.unicode.cldr.web.SurveyMain.Phase p = org.unicode.cldr.web.SurveyMain.Phase
									.valueOf(value.toUpperCase());
							value = p.name().toUpperCase();
						} catch (Throwable t) {
							valueErr = possiblePhases;
						}
					}

					if (valueErr == null) {
						String getFirst = survProps.getProperty(field, null);
						if (value.length() == 0) {
							survProps.remove(field);
						} else {
							if (!value.equals(getFirst)) {
								survProps.put(field, value);
							}
						}
						String getNow = survProps.getProperty(field, null);

						if (getNow != getFirst) {
		%><div class='okayText'>
			Set
			<tt><%=field%></tt>
			=
			<tt><%=value%></tt>
			<br />Updated cldr.properties..<%
				writeProps(survProps, propsFile, request);

								if (field.equals("CLDR_VAP")) {
									adminHtml.delete();
			%><h2>
				You've changed the CLDR_VAP password, great. Please: <br>#1
				restart the server, and <br>#2 click
				<%=clickHere%>
				to build admin.html before continuing. Thanks!
			</h2>
			<%
				// delete admin.html if PW changed 
								} // delete admin.html
			%>
		</div>
		<%
			} else {
		%><div class='squoText'>
			No change:
			<tt><%=field%></tt>
			=
			<tt><%=value%></tt>
		</div>
		<%
			}
					} else {
						which = whichF; // redo this one.
		%><div class='stopText'>
			Could not change
			<tt><%=field%></tt>
			to
			<tt><%=value%></tt>
			<br /><%=valueErr%>
			<%
				
			%>
		</div>
		<%
			}
				}
		%><div style='padding: 1em; margin: 1em; border: 1px solid gray;'>
			<%
				if (which < setupvars.length) {
			%>
			<h1 class='selected'><%=setupvars[which + 0]%></h1>
			<div class='st_setup_text'>
				<%=setupvars[which + 1]%>
			</div>
			<label><tt><%=setupvars[which + 0]%></tt>=<input size='80'
				name='<%=setupvars[which + 0]%>'
				value='<%=survProps.getProperty(setupvars[which + 0], "")%>'></label>
			<%
				} else {
			%>
			<ol class='st_setup'>
				<li>There must be a valid copy of CLDR in the CLDR_DIR
					directory (<tt class='codebox'><%=survProps.getProperty("CLDR_DIR")%></tt>).
					<%
					final String rootXmlPath = "common/main/root.xml";
							final File cldrDir = new File(
									survProps.getProperty(SurveyMain.CLDR_DIR));
							final File rootXml = new File(cldrDir, rootXmlPath);
							String fileErr = null;
							final String thePath = SurveyMain.CLDR_DIR + " ="
									+ cldrDir.getAbsolutePath() + " :";
							final String checkoutFix = "Please consider running <pre>svn checkout "
									+ SurveyMain.CLDR_DIR_REPOS
									+ "/trunk "
									+ cldrDir.getAbsolutePath()
									+ "</pre> to fix this situation, or go back and fix <b>CLDR_DIR</b> to point to a valid CLDR root. Then try reloading this page.";

							if (!cldrDir.isDirectory()) {
								fileErr = thePath + " not a directory. <br>"
										+ checkoutFix;
							} else if (!rootXml.canRead()) {
								fileErr = thePath + " - can't read " + rootXmlPath
										+ " <br>" + checkoutFix
										+ " (may need to delete the directory first)";
							}

							if (fileErr == null) {
				%>
					<div class='okayText'><%=thePath%></div> <%
 	} else {
 				errs++;
 %>
					<div class='stopText'><%=fileErr%></div> <%
 	}
 %>
				</li>



				<li>You must set up the database, see <a
					href='http://cldr.unicode.org/development/running-survey-tool/cldr-properties/db'>here</a>.
					<%
					boolean hasDS = false;
							try {
								hasDS = DBUtils.getInstance().hasDataSource();
							} catch (Throwable t) {
								errs++;
								out.println("Got this error:<pre style='border: 1px solid red; background-color: goldenrod; height: 20em; 	overflow: scroll; font-size: small;'>"
										+ t.toString() + "</pre>");
							}
							if (!hasDS) {
								errs++;
				%>
					<div class='stopText'>
						Database source was not found ..
						<pre><%=DBUtils.getDbBrokenMessage()%></pre>
					</div> <br> For MySQL, try the
					<button onclick='return mysqlhelp()'>MySQL Configurator</button>

					<p>Restart the web server and reload this page to try again.</li>
				<%
					} else {
				%>
				<div class='okayText'>Congratulations! You seem to have a
					database source up and running.</div>
				<%
					}
				%>


			</ol>


			<%
				}
			%>
		</div>
		<%
			if (which < setupvars.length - 1) {
					final int remain = ((setupvars.length - which) / 2);
		%>
		<button value='<%=which + 2%>' name='which'>
			<b> Save and Continue (<%=remain + " to go!"%>)
			</b>
		</button>
		<%
			if (remain == 1) {
		%>
		<i>(Note: this next step may take a little while - we may have to
			do some checkouts)</i>
		<%
			}
				}
			}
		%><input name='vap' type='hidden' value='<%=vap%>'>
	</form>

	<hr>
	<p>
		<%
			if (errs == 0) {
		%>
	
	<li>When you are done with ALL items, click this button and then
		restart the web server:
		<form
			action="<%=request.getContextPath() + request.getServletPath()%>"
			METHOD="POST">
			<input name='vap' type='hidden' value='<%=vap%>'><input
				name='remove_maint' type='submit' value='Remove Maint Mode'>
		</form>
	</li>
	<%
		} else {
	%>
	<div class='stopText'>Errors exist, please fix them and reload
		this page or restart the server.</div>
	<%
		}
	%>
	<hr>
	<a href='<%=request.getContextPath()%>'>Return to the SurveyTool
		main</a> | See documentation at
	<%=DOCLINK%>
</body>
</html>
