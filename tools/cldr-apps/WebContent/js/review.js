//review page 
//bind the event only on the review page 
function bindReviewEvents() {
    $('.help-comment').popover({placement: 'right'});//show the comment column
    
    $('.help-review-pop').popover({placement: 'left', trigger: 'hover'});//help in the review menu
    $('.tip').tooltip();//show tooltip

    refreshAffix();//refresh the review menu
}

$(function() {
	var dynamic = $('#DynamicDataSection');
	dynamic.on('click', '.collapse-review', togglePart);
	dynamic.on('click', 'button.fix', toggleFix);
	dynamic.on('click', '.hide-review', toggleReviewLine);
	dynamic.on('click', '.show-items', toggleItems);
	dynamic.on('click', '.post-review', openPost);
	dynamic.on('click', '.hide-review', toggleReview);
	
	$(window).scroll(function() {
		var left = $(this).scrollLeft();
			if(left != 0)
				$('#itemInfo').css('left', 1020 - left);
			else 
				$('#itemInfo').css('left',"");
			$('.navbar-fixed-top').css('left', 0 - left);
	});
	$(window).resize(function() {
		$('#itemInfo').css('left',"");
	});
	
	
	$('body').on('click','.show-examples',function() { //toggle the examples
		$('.d-example, .vote-help').slideToggle();
	});

});


//show the right menu in the review page
function showReviewMenu(numbers) {
	var info = $('#itemInfo');
	var menu = $('#navspy');
	info.html(menu.html());
	refreshAffix();
}


//handle the review page with the json
function showReviewPage(json) {
	var menuData = json.notification;
	var notifications = json.allNotifications;
	var menuRoot = $('#itemInfo');
	var notificationsRoot = $('#DynamicDataSection');
	var loadingBar = $('#LoadingMessageSection');
	var hidden = json.hidden;
	var menuDom = $(document.createElement('ul')).addClass('nav nav-pills nav-stacked affix menu-review');
	var direction = json.direction;
	
	//populate menu
	var activeMenu = true;
	$.each(menuData, function(index, element){
			var html = '';
			//active this menu first
			if(activeMenu) {
				html += '<li class="active">';
				activeMenu = false;
			}
			else
				html += '<li>';
			
			//inactive the one with no element
			html += '<a href="#'+element.name+'">';
			html += element.name.replace('_',' ')+' (<span class="remaining-count">0</span>/<span class="total-count">'+element.count+'</span>)<div class="pull-right"><span class="glyphicon glyphicon-info-sign help-review-pop" data-content="'+element.description+'"></span></div></a></li>';
			menuDom.append(html);
	});
	menuRoot.html(menuDom);
	
	//populate body
	var html = '';
	$.each(notifications, function(index, element) {
		//header
		if(element != 'null')
		$.each(element, function(index, element) {
			html += '<h3 class="collapse-review"><span id="'+index+'"></span><span class="glyphicon glyphicon-chevron-down chevron"></span>'+index.replace('_',' ')+' (<span class="remaining-count">0</span>/<span class="total-count"></span>)<label class="pull-right show-items"><button class="tip btn btn-default" data-toggle="button" title="Show hidden lines"><span class="glyphicon glyphicon-eye-open"></span></button></label></h3>';
			html += '<div class="table-wrapper" data-type="'+index+'"><table class="table table-responsive table-fixed-header table-review"><thead><tr><th>Code</th><th>English</th><th dir="'+direction+'">CLDR 24</th><th dir="'+direction+'">Winning 25</th><th dir="'+direction+'">Action</th></tr></thead><tbody>';
			
			$.each(element, function(index, element) {
				//body
				var catName = index;
				$.each(element, function(index, element) {
					var subCatName = index;
					$.each(element, function(index, element) {
						if(index != 'null')
							html += '<tr class="info"><td colspan="7"><b>'+catName+' - '+subCatName+'</b> : '+index+'</td></tr>';//blue banner
						else
							html += '<tr class="info"><td colspan="7"><b>'+catName+' - '+subCatName+'</b></td></tr>';//blue banner
						
						$.each(element, function(index, element){
							html += '<tr class="data-review" data-path=\''+element.path+'\'"><td class="button-review"><span class="link-main"><a target="_blank" href="'+getUrlReview(element.id)+'"><span class="label label-info">'+element.code+'  <span class="glyphicon glyphicon-share"></span></span></a></span></td><td>'+element.english+'</td><td dir="'+direction+'">'+element.old+'</td><td dir="'+direction+'">'+element.winning+'</td>';
							
							//fix section
							html += '<td class="button-review"><div class="tip fix-parent" title="Fix"><button type="button" class="btn btn-success fix" data-toggle="popover"><span class="glyphicon glyphicon-pencil"></span></button></div> <button type="button" class="btn btn-info hide-review tip" title="Hide"><span class="glyphicon glyphicon-eye-close"></span></button><button type="button" class="btn btn-primary tip post-review" title="Forum"><span class="glyphicon glyphicon-comment"></span></button>';
							if(element.comment)
								html += '<button class="btn btn-default help-comment" data-html="true" data-toggle="popover" data-content="'+element.comment+'"><span class="glyphicon glyphicon-info-sign"></span></button>';
							
							html +=	'</td></tr>';
							
							//fix content tr
							//html += '<tr class="fix-info" data-toggle="popover"><td colspan="7"><div class="fix-content well well-sm"></div></td></tr>';
						});
					});
					html += '<tr class="empty"><td colspan="7"></td></tr>';
				});
				
			});
		});
		html += '</tbody></table></div>';
	});
	notificationsRoot.html(html);
	notificationsRoot.show();
	loadingBar.hide();
	
	//show the alert ms
	popupAlert('warning', 'It is important that you read <a href="http://cldr.unicode.org/translation/vetting-view" target="_blank">Priority Items</a> before starting!');
	//hack to solve anchor issue
	$('#itemInfo .nav a').click(function(event){
		var href = $(this).attr('href').replace('#', '');
		var aTag = $('#'+ href);
		if(aTag.length)
			$('html,body').animate({scrollTop: aTag.offset().top},'slow');
		event.preventDefault();
		event.stopPropagation();
	});
	
	
	$.each(hidden, function(index,element) {
			var cat = index;
			$.each(element, function(index, element){
				$('div[data-type="'+cat+'"] tr[data-path="'+element+'"] .hide-review').click();
			});
	});
	refreshCounter();
	$('.menu-review li:visible').first().addClass('active');
	bindReviewEvents();
	
}

//get the url to link to the vetting page 
function getUrlReview(id) {
	return contextPath + '/v#/' + surveyCurrentLocale +'//'+id;
}

//save the hide line 
function toggleReview() {
	 var url = contextPath + "/SurveyAjax?&s="+surveySessionId+"&what=review_hide";
	 var path = $(this).parents('tr').data('path');
	 var choice = $(this).parents('.table-wrapper').data('type');
	 url += "&path="+path+"&choice="+choice+"&locale="+surveyCurrentLocale;
	 $.get(url, function(data) {
		 refreshCounter();
	 });

}


//TODO to optimize, do not update every label, only one need to -> refresh the counter from the review page
function refreshCounter() {
	var menus = $('.menu-review a');
	menus.each(function(index) {
		var element = $(this);
		var href = element.attr('href');
		var id = href.slice(1, href.length);
		var selection = $('div[data-type="'+id+'"] tr.data-review');
		var total = selection.length;
		var remaining = selection.not('.hidden-line').length;
		var counterList = $('#'+id).closest('h3');
		
		element.children('.remaining-count').text(remaining);
		element.children('.total-count').text(total);
		if(total == 0)
			element.closest('li').remove();
		counterList.children('.remaining-count').text(remaining);
		counterList.children('.total-count').text(total);
	});
}


//slideToggle the notifications type
function togglePart() {
    var table = $(this).next();
    var glyph = $(this).find('.glyphicon.chevron');
    
    glyph.toggleClass('glyphicon-chevron-down').toggleClass('glyphicon-chevron-right');
    table.slideToggle();
    
    refreshAffix();
}

//open a slide with the fix button
function toggleFix(event) {
	var tr = $(this).closest('tr');
    var button = $(this);	
    var isPopover = button.parent().find('.popover').length === 1;
	$('button.fix').popover('destroy');
	toggleOverlay();
	if(!isPopover) {
	    var url = contextPath + "/RefreshRow.jsp?what="+WHAT_GETROW+"&json=t&_="+surveyCurrentLocale+"&s="+surveySessionId+"&xpath="+tr.data('path')+"&strid="+surveyCurrentId+cacheKill()+"&dashboard=true";
	    myLoad(url, "section", function(json) {
	    			isLoading=false;
	    			theDiv = document.createElement("div");
	    			theDiv.id = "popover-vote";
	    			if(json.section.nocontent) {
	    				surveyCurrentSection = '';
	    			} else if(!json.section.rows) {
	    				console.log("!json.section.rows");
	    				handleDisconnect("while loading- no rows",json);
	    			} else {
	    				stdebug("json.section.rows OK..");
	    				if(json.dataLoadTime) {
	    					updateIf("dynload", json.dataLoadTime);
	    				}
	    				if(!surveyUser) {
	    					showInPop2(stui.str("loginGuidance"), null, null, null, true); /* show the box the first time */
	    				} else if(!json.canModify) {
	    					showInPop2(stui.str("readonlyGuidance"), null, null, null, true); /* show the box the first time */
	    				} else {
	    					showInPop2(stui.str("dataPageInitialGuidance"), null, null, null, true); /* show the box the first time */
	    				}
	    				
	    					 
	    				insertFixInfo(theDiv,json.pageId,surveySessionId,json); 
	    				
	    				//display the popover
	    				if(button.parent().find('.popover:visible').length == 0)
	    					button.popover('destroy');
	    				button.popover({placement:"left",html:true,content:'<div></div>', title:showAllProblems(json.issues) + tr.children('td').first().html(), animation:false}).popover('show');
	    				button.data('issues', json.issues);
	    				//check if we have to suppress the line 
	    				button.on('hidden.bs.popover', checkLineFix);
	    				
	    				//add the row to the popover
	    				var content = button.parent().find('.popover-content').get(0);
	    				content.appendChild(theDiv);
	    				
	    				//hack/redesign it
	    				designFixPanel();
	    				
	    				//correct the position of the popover
	    				fixPopoverVotePos();
	    		}
	    });
	}
	
    refreshAffix();
    event.preventDefault();
    event.stopPropagation();
    return false;
}

//insert the row for the fix button 
function insertRowReview(theTable,tbody, row, tr) {
	theTable.hitCount++;
	removeAllChildNodes(tbody);
		if(!theRow) {
			console.log("Missing row " + k);
		}
		updateRow(tr,theRow);
		tbody.appendChild(tr);
}

//hide or show line for the review page
function toggleReviewLine() {
    var line = $(this).closest('tr');
    var next = line.next();
    
    if (line.hasClass('hidden-line')) {
        line.removeClass('hidden-line');
        if(next.hasClass('fix-info'))
            next.removeClass('hidden-line'); //for the fix menu
        $(this).removeClass('btn-warning').addClass('btn-info');
        $(this).find('.glyphicon').removeClass('glyphicon-eye-open').addClass('glyphicon-eye-close');
        $(this).tooltip('hide').attr('title','Hide').tooltip('fixTitle');
    }
    else {
        line.addClass('hidden-line');
        if(next.hasClass('fix-info'))
            next.addClass('hidden-line');
        $(this).removeClass('btn-info').addClass('btn-warning');
        $(this).find('.glyphicon').removeClass('glyphicon-eye-close').addClass('glyphicon-eye-open');
        $(this).tooltip('hide').attr('title','Show').tooltip('fixTitle');
    }

    //hide blue banner
    var info = line.prevAll(".info:first");//get the closet previous
    var hide = true;
    info.nextUntil('.info').each(function(index) { //check if all sublines are hidden or not a real line
        if (!($(this).hasClass('hidden-line') || $(this).hasClass('empty'))) {
            hide = false;
        }
    });
    
    if (hide) {
        info.addClass('hidden-line');
        info.nextUntil('.empty').last().next().addClass('hidden-line');        
    }
    else {
        info.removeClass('hidden-line');
        info.nextUntil('.empty').last().next().removeClass('hidden-line');
    }


    var table = info.parents('table');

    if (table.find('tr:not(.hidden-line):not(.empty)').length > 1) { //there is only the header staying not hidden
        table.find('tr:first').removeClass('hidden-line');
    //    table.find('tr.empty').removeClass('hidden-line');

    }
    else {
        table.find('tr:first').addClass('hidden-line');
      //  table.find('tr.empty').addClass('hidden-line');
    }
    
    refreshAffix();
}

//force to show the actual hidden line
function toggleItems(event) {
	
	var input = $(this).children('button');
    if (input.hasClass('active')) {
        $('tr').removeClass('shown');
        $('.show-items button').removeClass('active');
    }
    else {
        $('tr').addClass('shown');
        $('.show-items button').addClass('active');
    }
    
    refreshAffix();
    event.stopPropagation();
    event.preventDefault();
}

//refresh affix (right menu).
function refreshAffix() {
    $('[data-spy="scroll"]').each(function () {
        var $spy = $(this).scrollspy('refresh');
    });
}

//add or remove line depending if we solved the issue or created new
function checkLineFix() {
	var line = $(this).closest('tr');
	var info = line.prevAll(".info:first");
	var path = line.data('path');
	var issues = $(this).data('issues');
	
	var lines = $('tr[data-path='+path+']');
	lines.each(function() {
		if($(this).hasClass('success')) {
			$(this).fadeOut('slow', function() {
				var inf = $(this).prevAll(".info:first");
				$(this).remove();
			    if(inf.next('.data-review').length == 0) {
			    	inf.next('.empty').remove();
			    	inf.remove();
			    }
			    
			    if($('.fix-parent .popover').length)
			    	fixPopoverVotePos();
			    
			    refreshCounter();
			});
			
		}
	})
	
	
	$.each(issues, function(index, element) {
		var otherLine = $('div[data-type='+element+'] tr[data-path='+path+']');
		if(otherLine.length == 0) { //if line not present
			var newLine = line.clone();
			var found = false;
			$('div[data-type='+element+'] .info').each(function() {
				if(info.html() == $(this).html()) {
					$(this).after(newLine);
					found = true;
				}
			});
			
			
			if(!found) {
				var html = '<tr class="info">'+info.html()+'</tr>'+newLine.wrap('<div>').parent().html()+'<tr class="empty"><td colspan="7"></td></tr>';
				var toInsert = $('div[data-type='+element+'] > table > tbody');
				toInsert.prepend(html);
				console.log(html);
			}
		}
	});
	
	bindReviewEvents();
	refreshCounter();
}

//refresh the fix panel
function refreshFixPanel(json) {
	var issues = json.issues;
	var theDiv = $('#popover-vote').get(0);
	theDiv.innerHTML = '';
	
	insertFixInfo(theDiv,json.pageId,surveySessionId,json); 
	designFixPanel();
	fixPopoverVotePos();
	
	var line = $('.fix-parent .popover').closest('tr');
	var path = line.data('path');
	$('tr[data-path='+path+']').each(function() {
		var type = $(this).closest('.table-wrapper').data('type');
		if($.inArray(type,issues) == -1)
			$(this).addClass('success');
		else
			$(this).removeClass('success');
	});

	$('.fix-parent .popover-title').text(showAllProblems(issues) + line.children('td').first().text());


	if($('.data-vertical .vote-help').length == 0) {
		$('.data-vertical .vote-help').remove();
		$('.data-vertical #comparisoncell .d-example').after($('.data-vote .vote-help'));
		$('.vote-help').hide();
	}
	else 
		$('.data-vote .vote-help').remove();
	
	line.find('button.fix').data('issues',issues);
}

//vote summary part 
function showHelpFixPanel(cont) {
	$('.fix-parent .data-vote').html('');
	$('.fix-parent .data-vote').append(cont);
	
	$('.data-vote > .span, .data-vote > .pClassExplain').remove();
	$('.data-vote > .span, .data-vote > .d-example').remove();
	
	var helpBox =  $('.data-vote > *:not(.voteDiv)').add('.data-vote hr');
	$('.data-vote table:last').after(helpBox);
	
	if($('.trInfo').length != 0) {
		$('.voteDiv').prepend('<hr/>');
		$('.voteDiv').prepend($('.trInfo').parent());
	}
	
	
	//move the element
	labelizeIcon();
}

//end of fix part





//open a thread of post concerning this xpath
function openPost() {
	var path = $(this).closest(".data-review").data('path');
	var choice = $(this).closest(".table-wrapper").data('type');
	var postModal = $('#post-modal');
	var locale = surveyCurrentLocale;
	var url = contextPath + "/SurveyAjax?&s="+surveySessionId+"&what=forum_fetch&xpath="+$(this).closest('tr').data('path')+"&voteinfo&_="+locale;
	postModal.modal();

	$.get(url, function(data){
		var content = '';
		var post = data.ret;
		content += '<form role="form" id="post-form">';
		content += '<div class="form-group"><textarea name="text" class="form-control" placeholder="Write your post here"></textarea></div><button data-path="'+path+'" data-choice="'+choice+'" class="btn btn-success submit-post btn-block">Submit</button>';
		
		content += '<input type="hidden" name="forum" value="'+surveyCurrentLocale.substring(0,2)+'">';
		content += '<input type="hidden" name="_" value="'+surveyCurrentLocale+'">';
		content += '<input type="hidden" name="replyto" value="x'+path+'">';
		content += '<input name="subj" type="hidden" value="Review">';
		content += '<input name="post" type="hidden" value="Post">';
		content += '<input name="isReview" type="hidden" value="1">';
		content += '</form>';
		
		if(post) {
			$.each(post, function(index, element) {
					content += generateHTMLPost(element);
			})
		}
		content += '<div class="post"></div>';
		postModal.find('.modal-body').html(content);

		
		postModal.find('textarea').autosize();
		postModal.find('.submit-post').click(submitPost);
		setTimeout(function() {postModal.find('textarea').focus();},1000);
	}, 'json');
	
}

//generate the HTML for a given post
function generateHTMLPost(post) {
	var html = '';	
	html += '<div class="well well-sm post">'; 
	
	
	html += '<h4>'+post.posterInfo.name +' ('+post.posterInfo.org+')<span class="label label-info" style="margin-left:5px;">'+post.posterInfo.userlevelName+'</span>';
	html += '<span class="label label-primary pull-right">'+post.date+'</span></h4>';
	html += '<div class="content">'+post.text.replace(/\n/g, '<br />')+'</div>';
	html += '</div>';
	
	return html;
}

//submit the post
function submitPost(event) {
	var locale = surveyCurrentLocale;
	var url = contextPath + "/survey";
	var form = $('#post-form');
	if($('#post-form textarea[name=text]').val()) {
		
		$.ajax({
	        data: form.serialize(),
	        type: "POST",
	        url: url,
	        contentType: "application/x-www-form-urlencoded;",
	        dataType: 'json',
	        success: function(data) {
	        	var post = $('.post').first();
				post.before(generateHTMLPost(data));
				
				//reset
				post = $('.post').first();
				post.hide();
				post.show('highlight', {color : "#d9edf7"});
				$('#post-form textarea').val('');
	        }
	});

	}
	event.preventDefault();
	event.stopPropagation();
}

//insert the row fix in the popover
function insertFixInfo(theDiv,xpath,session,json) {
		var theTable = theDiv.theTable;
		var doInsertTable = null;
		
		removeAllChildNodes(theDiv);
		window.insertLocaleSpecialNote(theDiv);
			theTable = cloneLocalizeAnon(document.getElementById("proto-datafix"));
			theTable.className = 'data dashboard';
			updateCoverage(theDiv);
			localizeFlyover(theTable);
			var toAdd = cloneLocalizeAnon(document.getElementById("proto-datarowfix"));  // loaded from "hidden.html", which see.
			/*if(!surveyConfig)*/ {
				var rowChildren = getTagChildren(toAdd);
				theTable.config = surveyConfig ={};
				for(var c in rowChildren) {
					if(rowChildren[c].id) {
						surveyConfig[rowChildren[c].id] = c;
						stdebug("  config."+rowChildren[c].id+" = children["+c+"]");
						if(false&&stdebug_enabled) {
							removeAllChildNodes(rowChildren[c]);
							rowChildren[c].appendChild(createChunk("config."+rowChildren[c].id+"="+c));
						}
						//rowChildren[c].id=null;
					} else {
						stdebug("(proto-datarow #"+c+" has no id");
					}
				}
				if(stdebug_enabled) stdebug("Table Config: " + JSON.stringify(theTable.config));
			}
			theTable.toAdd = toAdd;
			theTable.myTRs = [];
			theDiv.theTable = theTable;
			theTable.theDiv = theDiv;
			doInsertTable=theTable;
		// append header row
		
		theTable.json = json;
		theTable.xpath = xpath;
		theTable.hitCount=0;
		theTable.session = session;

		var tbody = $(theTable).children('.data-vertical').get(0);
		theTable.hitCount++;
		
		if(!theTable.curSortMode) { 
			theTable.curSortMode = theTable.json.displaySets["default"];
			// hack - choose one of these
			if(theTable.json.displaySets.codecal) {
				theTable.curSortMode = "codecal";
			} else if(theTable.json.displaySets.metazon) {
				theTable.curSortMode = "metazon";
			}
		}
		
		var k = theTable.json.displaySets[theTable.curSortMode].rows[0];
		var theRow = theTable.json.section.rows[k];
		removeAllChildNodes(tbody);
		var tr = theTable.myTRs[k];
		if(!tr) {
			tr = cloneAnon(theTable.toAdd);
			theTable.myTRs[k]=tr; // save for later use
		}
		tr.rowHash = k;
		tr.theTable = theTable;
		if(!theRow) {
			console.log("Missing row " + k);
		}
		updateRow(tr,theRow);
				
		tbody.appendChild(tr);	
		theDiv.appendChild(doInsertTable);
}

//redesign the fix row
function designFixPanel() {
	wrapRadios();

	var nocell = $('.fix-parent #popover-vote .data-vertical #nocell');
	var idnocell = nocell.find('input').attr('id');
	nocell.children('div').append('<span class="subSpan">Abstain</span>');	
	
	var statuscell = $('.fix-parent #popover-vote .data-vertical #statuscell');
	var statusClass = statuscell.get(0).className;
	statuscell.get(0).className = '';
	
	var comparisoncell = $('.fix-parent  #popover-vote .data-vertical #comparisoncell');
	comparisoncell.find('.btn').remove();
	if(!comparisoncell.find('.subSpan').length) {
		comparisoncell.contents()
		  .filter(function(){
		    return this.nodeType === 3;
		  })
		  .wrap('<span class="subSpan"></span>');
	}
	
	var exampleButton = $('<button title="Show examples" class="btn btn-default show-examples"><span class="glyphicon glyphicon-list"></span></button>');
	comparisoncell.prepend(exampleButton);
	exampleButton.tooltip();

	//clean 
	if(!idnocell) {
		nocell.next().remove(); //the hr
		nocell.html('');
	}
	
	//add status of the winning item
	$('.fix-parent #proposedcell .subSpan .winner').after('<div class="status-comparison '+statusClass+'"></div>');
	
	//replace default by success on the selected one
	$('#popover-vote input[type="radio"]:checked').closest('.btn').removeClass('btn-default').addClass('btn-info');
	
	//add some help 
	$('.fix-parent #nocell .subSpan').append('<div class="help-vote"></div>');
	$('.fix-parent #comparisoncell .subSpan .help-vote').remove();
	$('.fix-parent #comparisoncell .subSpan').append('<div class="help-vote">English source</div>');
	$('.fix-parent #proposedcell .subSpan').append('<div class="help-vote">Winning translation</div>');
	$('.fix-parent #othercell .form-inline').after('<span class="subSpan"><div class="help-vote">Add a translation</div></span>');
	if($('.fix-parent #othercell .d-item').length)
		$('.fix-parent #othercell hr').first().after('<span class="subSpan"><div class="help-vote">Other translation(s)</div></span>');
	
	//remove unnecessary header
	$('#popover-vote .warnText').remove();
	
	$('.d-example').hide();
	fixPopoverVotePos();
	labelizeIcon();
	
	
	$('.fix-parent #proposedcell').click();
	$('.data .close').click(function() {$(this).closest('.popover').prev().popover('hide');$('.tip').tooltip('hide');});
	
	
}

//reposition the popover manually
function fixPopoverVotePos() {
	var button = $('.fix-parent #popover-vote').closest('.fix-parent').find('.fix');
	var popover = button.parent().find('.popover');
	var decal = 75;
	popover.css('top', button.position().top - decal);
	popover.css('z-index',998);
	popover.children('.arrow').css('top',decal + button.outerHeight(true)/2);
}

//add to the radio button, a more button style
function wrapRadios() {
	var radios = $('.ichoice-o, .ichoice-x');
	radios = radios.filter(function() {return $(this).parent('.btn').length === 0;});
	radios.wrap('<label class="btn btn-default" title="Vote"></label>');
	radios.parent().parent().wrapInner('<div></div>');
	radios.parent().tooltip();
}

//display an array of issues inline
function showAllProblems(issues) {
	var string = '';
	$.each(issues, function(index, element){
		if(string)
			string += ',';
		
		string += ' '+ element;
	});
	
	return string;
}