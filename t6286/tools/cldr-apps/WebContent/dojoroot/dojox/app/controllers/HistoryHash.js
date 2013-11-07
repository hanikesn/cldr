//>>built
define("dojox/app/controllers/HistoryHash",["dojo/_base/lang","dojo/_base/declare","dojo/topic","dojo/on","../Controller","dojo/hash"],function(_1,_2,_3,on,_4){
return _2("dojox.app.controllers.HistoryHash",_4,{constructor:function(_5){
this.events={"startTransition":this.onStartTransition};
this.inherited(arguments);
_3.subscribe("/dojo/hashchange",_1.hitch(this,function(_6){
this._onHashChange(_6);
}));
this._historyStack=[];
this._historyLen=0;
this._current=null;
this._next=null;
this._previous=null;
this._index=0;
this._oldHistoryLen=0;
this._newHistoryLen=0;
this._addToHistoryStack=false;
this._detail=null;
this._startTransitionEvent=false;
var _7=window.location.hash;
if(_7&&(_7.length>1)){
_7=_7.substr(1);
}
this._historyStack.push({"hash":_7,"url":window.location.href,"detail":{target:_7}});
this._historyLen=window.history.length;
this._index=this._historyStack.length-1;
this._current=_7;
this._historyDiff=window.history.length-this._historyStack.length;
},onStartTransition:function(_8){
var _9=_8.detail.target;
var _a=/#(.+)/;
if(!_9&&_a.test(_8.detail.href)){
_9=_8.detail.href.match(_a)[1];
}
var _b=_8.detail.url||"#"+_9;
this._oldHistoryLen=window.history.length;
window.location.hash=_b;
this._addToHistoryStack=true;
this._detail=_8.detail;
this._startTransitionEvent=true;
},_addHistory:function(_c){
this._historyStack.push({"hash":_c,"url":window.location.href,"detail":{target:_c}});
this._historyLen=window.history.length;
this._index=this._historyStack.length-1;
this._previous=this._current;
this._current=_c;
this._next=null;
this._historyDiff=window.history.length-this._historyStack.length;
this._addToHistoryStack=false;
},_onHashChange:function(_d){
if(this._index<0||this._index>(window.history.length-1)){
throw Error("Application history out of management.");
}
this._newHistoryLen=window.history.length;
if(this._oldHistoryLen>this._newHistoryLen){
this._historyStack.splice((this._newHistoryLen-this._historyDiff-1),(this._historyStack.length-1));
this._historyLen=this._historyStack.length;
this._oldHistoryLen=0;
}
if(this._addToHistoryStack&&(this._oldHistoryLen===this._newHistoryLen)){
this._historyStack.splice((this._newHistoryLen-this._historyDiff-1),(this._historyStack.length-1));
this._addHistory(_d);
return;
}
if(this._historyLen<window.history.length){
this._addHistory(_d);
if(!this._startTransitionEvent){
this.app.trigger("transition",{"viewId":_d});
}
}else{
if(_d==this._current){
}else{
if(_d===this._previous){
this._back(_d,this._historyStack[this._index]["detail"]);
}else{
if(_d===this._next){
this._forward(_d,this._historyStack[this._index]["detail"]);
}else{
var _e=-1;
for(var i=this._index;i>0;i--){
if(_d===this._historyStack[i]["hash"]){
_e=i;
break;
}
}
if(-1===_e){
for(var i=this._index;i<this._historyStack.length;i++){
if(_d===this._historyStack[i]["hash"]){
_e=i;
break;
}
}
}
if(0<_e<this._historyStack.length){
this._go(_e,(_e-this._index));
}else{
}
}
}
}
}
this._startTransitionEvent=false;
},_back:function(_f,_10){
this._next=this._historyStack[this._index]["hash"];
this._index--;
if(this._index>0){
this._previous=this._historyStack[this._index-1]["hash"];
}else{
this._previous=null;
}
this._current=_f;
_3.publish("/app/history/back",{"viewId":_f,"detail":_10});
this.app.trigger("transition",{"viewId":_f,"opts":{reverse:true}});
},_forward:function(_11,_12){
this._previous=this._historyStack[this._index]["hash"];
this._index++;
if(this._index<this._historyStack.length-1){
this._next=this._historyStack[this._index+1]["hash"];
}else{
this._next=null;
}
this._current=_11;
_3.publish("/app/history/forward",{"viewId":_11,"detail":_12});
this.app.trigger("transition",{"viewId":_11,"opts":{reverse:false}});
},_go:function(_13,_14){
if(_13<0||(_13>window.history.length-1)){
throw Error("Application history.go steps out of management.");
}
this._index=_13;
this._current=this._historyStack[_13]["hash"];
this._previous=this._historyStack[_13-1]?this._historyStack[_13-1]["hash"]:null;
this._next=this._historyStack[_13+1]?this._historyStack[_13+1]["hash"]:null;
_3.publish("/app/history/go",{"viewId":this._current,"step":_14,"detail":this._historyStack[_13]["detail"]});
var _15;
if(_14>0){
_15={"viewId":this._current,"opts":{reverse:false}};
}else{
_15={"viewId":this._current,"opts":{reverse:true}};
}
this.app.trigger("transition",_15);
}});
});
