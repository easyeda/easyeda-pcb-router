/**
 * 自动布线接口程序
 */

//外部变量名可根据实际需要修改, 闭包内的代码及命名无须做任何改动.
easyeda.AutoRouter=(function(){
    /**
     * 自动布线器构造函数.
     * @param serverURL 服务程序的地址,如 wss://xxx.xx.xx.xx:xxx/router
     * @constructor
     */
    function AutoRouter(serverURL){
        this._intervalId=0;
        this._serverURL=serverURL;
        this._manualClose=false;
        this._ws=null;
        /**
         * 当出现错误时调用
         * @type {Function} 无参数
         */
        this.onError=null;

        /**
         * 布线中间结果返回,可将中间布线结果显示出来.
         * @type {Function} 参数为netArr,inCompleteNetNum, 其中netArr可能为null(只发inCompleteNetNum的情况)
         */
        this.onProgress=null;
        /**
         * 布线结果回调
         * @type {Function} 参数为 resultCode,netArr,inCompleteNetNum
         *      其中, resultCode为结果码,对应下方的常量定义,  netArr是将session文件转换后的netArr数组.
         */
        this.onResult=null;
    }
    /**
     * 请求布线,回调设置后调用
     * @param dsnData Autorouter可识别的dsn文件数据
     * @param timeout 期望最大布线时间,以秒为单位.
     * @param progressInterval 期望返回中间结果的间隔,以秒为单位,必须为整数, 0表示不返回中间结果.
     * @param optimizeTime 优化时间,以秒为单位
     */
    AutoRouter.prototype.requestRoute=function(dsnData,timeout,progressInterval,optimizeTime){
        if(this._ws!=null){
            throw "Previous connection must be close first (call close function)";
        }
        var ws=new WebSocket(this._serverURL);
        var me=this;
        this._manualClose=false;
        ws.onopen=function(){
            var packet={"a":"startRoute","data":dsnData,"timeout":timeout,"progressInterval":progressInterval,"optimizeTime":optimizeTime};
            ws.send(JSON.stringify(packet));
            me._intervalId=setInterval(function(){me._heartbeat();},15000);
        }
        ws.onmessage=function(e){
            var r=JSON.parse(e.data);
            if(r['a']=='routingResult'){
                if(me.onResult) me.onResult(r['complete'],r['data'],r['inCompleteNetNum']);
            }
            else if(r['a']=='routingProgress'){
                if(me.onProgress) me.onProgress(r['data'],r['inCompleteNetNum']);
            }
        }
        ws.onerror=function(){
            if(me.onError) me.onError();
        }
        ws.onclose=function(){
            if(!me._manualClose && me.onError) me.onError();
        }
        this._ws=ws;
    }
    AutoRouter.prototype.close=function(){
        if(!this._ws) return;
        this._manualClose=true;
        this._ws.close();
        this._ws=null;
        clearInterval(this._intervalId);
    }

    AutoRouter.prototype._heartbeat=function(){
        this._ws.send(JSON.stringify({"a":"heartbeat"}));
    }

    AutoRouter.RESULT_CODE_COMPLETE = 1;  //结果码,表示布线已经全部完成
    AutoRouter.RESULT_CODE_NOT_COMPLETE = 0;  //结果码, 表示因为超时中断,布线并未完成.
    AutoRouter.RESULT_CODE_SERVER_BUSY = -1;  //结果码, 表示服务器正在同时布线的进程进达上限,提醒用户稍候重试
    AutoRouter.RESULT_CODE_ERROR_OPEN_FILE = -2;  //结果码, 服务器无法打开该文件
    return AutoRouter;
})();



api('ripupAllNet');


//测试demo,下面的代码必须在"运行脚本代码"中执行. 执行该代码后需要耐心等待一段时间即可直接看到自动布线结果.
//添加了布线的中间结果返回,虽然服务器会定时推送中间结果,但最开始请求时由于服务器启动布线程序需要花点时间,所以还是建议前端做好提示.
(function(){
    var router=new easyeda.AutoRouter("wss://router.easyeda.com:8443/router");
    router.onError=function(){
        alert("router error.");
        router.close();
        Va();
    }
    router.onResult=function(resultCode,netArr,inCompleteNetNum){
        if(resultCode==easyeda.AutoRouter.RESULT_CODE_SERVER_BUSY){
            alert("服务器忙,请稍候再试.");
            router.close();
            return;
        }
        alert("布线最终结果码: "+resultCode+" 未完成连接:"+inCompleteNetNum);
        api('ripupAllNet'); //最终结果需要清空上次的连线.
        api('importSession',JSON.stringify(netArr));
        router.close();
        Va();
    }
    router.onProgress=function(netArr,inCompleteNetNum){
        console.log("progress inCompleteNetNum="+inCompleteNetNum);

        if(netArr!=null){
            api('ripupAllNet');//每个中间结果需要清空上次的连线.
            api('importSession',JSON.stringify(netArr));
        }
    }
    var data=api('exportDSN', {'width': '8.1mil', 'clearance' : '11mil'});
    router.requestRoute(data,30,10,5);
    Cc();
})();

