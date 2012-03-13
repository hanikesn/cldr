<%@page import="org.unicode.cldr.util.VettingViewer"%>
<%@ page import="org.unicode.cldr.web.*" %>
<!--  begin ajax_status.jsp -->
<script type='text/javascript' src='<%= request.getContextPath()+"/dojoroot/dojo/dojo.js" %>'
    djConfig='parseOnLoad: true, isDebug: false'></script>
<script type="text/javascript">
// just things that must be JSP generated
var surveyRunningStamp = '<%= SurveyMain.surveyRunningStamp.current() %>';
var contextPath = '<%= request.getContextPath() %>';
<%
String surveyCurrentLocale = request.getParameter(SurveyMain.QUERY_LOCALE);
String surveyCurrentSection = request.getParameter(SurveyMain.QUERY_SECTION);
String surveyCurrentForum = request.getParameter(SurveyForum.F_XPATH);
if(surveyCurrentLocale!=null&&surveyCurrentLocale.length()>0&&
    (surveyCurrentSection!=null||surveyCurrentForum!=null)) {
%>
var surveyLocaleUrl='&<%= SurveyMain.QUERY_LOCALE %>=<%= surveyCurrentLocale %>';
var surveyCurrentLocale = '<%= surveyCurrentLocale %>';
var surveyCurrentLocaleStamp = 0;
<% }else{ %>
var surveyCurrentLocale = null;
var surveyCurrentLocaleStamp = 0;
var surveyLocaleUrl='';
<% } %>
var warnIcon = "<%= WebContext.iconHtml(request,"warn","Test Warning") %>";
var stopIcon = "<%= WebContext.iconHtml(request,"stop","Test Error") %>";
var WHAT_GETROW = "<%= SurveyAjax.WHAT_GETROW %>";
var WHAT_SUBMIT = "<%= SurveyAjax.WHAT_SUBMIT %>";
</script>
<script type='text/javascript' src='<%= request.getContextPath() %>/js/survey.js'></script>

<%= VettingViewer.getHeaderStyles() %>

<% if(!SurveyMain.isUnofficial) { out.println(org.unicode.cldr.tool.ShowData.ANALYTICS); } %>
<!--  end ajax_status.jsp -->
