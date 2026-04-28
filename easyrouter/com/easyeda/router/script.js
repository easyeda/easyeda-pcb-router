/**
 * EasyEDA PCB 自动布线客户端 API
 *
 * 提供浏览器端 WebSocket 客户端，用于连接 EasyEDA 本地/云端自动布线服务器。
 * 通过 WebSocket 发送 DSN 设计数据，接收布线结果和中间进度。
 *
 * EasyEDA PCB Auto-Router Client API.
 * Browser-side WebSocket client for connecting to the EasyEDA local/cloud
 * auto-routing server. Sends DSN design data and receives routing results
 * and intermediate progress updates.
 *
 * @example
 * var router = new easyeda.AutoRouter("wss://router.easyeda.com:8443/router");
 * router.onResult = function(resultCode, netArr, inCompleteNetNum) { ... };
 * router.onProgress = function(netArr, inCompleteNetNum) { ... };
 * router.onError = function() { ... };
 * var dsnData = api('exportDSN', {'width': '8.1mil', 'clearance': '11mil'});
 * router.requestRoute(dsnData, 30, 10, 5);
 */

//外部变量名可根据实际需要修改, 闭包内的代码及命名无须做任何改动.
easyeda.AutoRouter=(function(){
    /**
     * 自动布线器构造函数.
     *
     * @param {string} serverURL WebSocket 服务地址, 如 "wss://router.easyeda.com:8443/router"
     *                           或本地 "ws://127.0.0.1:3579/router"
     * @constructor
     */
    function AutoRouter(serverURL){
        this._intervalId=0;
        this._serverURL=serverURL;
        this._manualClose=false;
        this._ws=null;
        /**
         * 错误回调，连接异常或非主动关闭时触发。
         * Error callback, triggered on connection error or unexpected close.
         * @type {Function|null} 无参数 / No parameters
         */
        this.onError=null;

        /**
         * 布线中间进度回调，服务器定时推送当前布线快照。
         * Progress callback, server pushes intermediate routing snapshots periodically.
         * @type {Function|null}
         * @param {Array|null} netArr  布线网络数组（可能为 null，仅推送计数）/ Net array (may be null)
         * @param {number} inCompleteNetNum 未完成连接数 / Incomplete net count
         */
        this.onProgress=null;
        /**
         * 布线最终结果回调。
         * Final routing result callback.
         * @type {Function|null}
         * @param {number} resultCode 结果码 / Result code (see RESULT_CODE_* constants)
         * @param {Array}  netArr     布线网络数组 / Routed net array for importSession
         * @param {number} inCompleteNetNum 未完成连接数 / Incomplete net count
         */
        this.onResult=null;
    }
    /**
     * 发起布线请求。调用前需先设置 onResult/onProgress/onError 回调。
     * Start a routing request. Set onResult/onProgress/onError callbacks before calling.
     *
     * @param {string} dsnData           Specctra DSN 格式的设计数据 / DSN file content string
     * @param {number} timeout           最大布线时间（秒）/ Max routing time in seconds
     * @param {number} progressInterval  中间结果推送间隔（秒），0 表示不推送 / Progress interval in seconds, 0 to disable
     * @param {number} optimizeTime      布线后优化时间（秒）/ Post-route optimization time in seconds
     * @throws {string} 如果上一个连接未关闭 / If previous connection is still open
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

