(self.webpackChunk_N_E=self.webpackChunk_N_E||[]).push([[643],{5095:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),function(e,t){for(var r in t)Object.defineProperty(e,r,{enumerable:!0,get:t[r]})}(t,{noSSR:function(){return o},default:function(){return u}});let n=r(1538);r(4246),r(7378);let a=n._(r(2995));function i(e){return{default:(null==e?void 0:e.default)||e}}function o(e,t){return delete t.webpack,delete t.modules,e(t)}function u(e,t){let r=a.default,n={loading:e=>{let{error:t,isLoading:r,pastDelay:n}=e;return null}};e instanceof Promise?n.loader=()=>e:"function"==typeof e?n.loader=e:"object"==typeof e&&(n={...n,...e});let u=(n={...n,...t}).loader;return(n.loadableGenerated&&(n={...n,...n.loadableGenerated},delete n.loadableGenerated),"boolean"!=typeof n.ssr||n.ssr)?r({...n,loader:()=>null!=u?u().then(i):Promise.resolve(i(()=>null))}):(delete n.webpack,delete n.modules,o(r,n))}("function"==typeof t.default||"object"==typeof t.default&&null!==t.default)&&void 0===t.default.__esModule&&(Object.defineProperty(t.default,"__esModule",{value:!0}),Object.assign(t.default,t),e.exports=t.default)},2254:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),Object.defineProperty(t,"LoadableContext",{enumerable:!0,get:function(){return n}});let n=r(1538)._(r(7378)).default.createContext(null)},2995:function(e,t,r){"use strict";/**
@copyright (c) 2017-present James Kyle <me@thejameskyle.com>
 MIT License
 Permission is hereby granted, free of charge, to any person obtaining
a copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, sublicense, and/or sell copies of the Software, and to
permit persons to whom the Software is furnished to do so, subject to
the following conditions:
 The above copyright notice and this permission notice shall be
included in all copies or substantial portions of the Software.
 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE
*/Object.defineProperty(t,"__esModule",{value:!0}),Object.defineProperty(t,"default",{enumerable:!0,get:function(){return d}});let n=r(1538)._(r(7378)),a=r(2254),i=[],o=[],u=!1;function c(e){let t=e(),r={loading:!0,loaded:null,error:null};return r.promise=t.then(e=>(r.loading=!1,r.loaded=e,e)).catch(e=>{throw r.loading=!1,r.error=e,e}),r}class l{promise(){return this._res.promise}retry(){this._clearTimeouts(),this._res=this._loadFn(this._opts.loader),this._state={pastDelay:!1,timedOut:!1};let{_res:e,_opts:t}=this;e.loading&&("number"==typeof t.delay&&(0===t.delay?this._state.pastDelay=!0:this._delay=setTimeout(()=>{this._update({pastDelay:!0})},t.delay)),"number"==typeof t.timeout&&(this._timeout=setTimeout(()=>{this._update({timedOut:!0})},t.timeout))),this._res.promise.then(()=>{this._update({}),this._clearTimeouts()}).catch(e=>{this._update({}),this._clearTimeouts()}),this._update({})}_update(e){this._state={...this._state,error:this._res.error,loaded:this._res.loaded,loading:this._res.loading,...e},this._callbacks.forEach(e=>e())}_clearTimeouts(){clearTimeout(this._delay),clearTimeout(this._timeout)}getCurrentValue(){return this._state}subscribe(e){return this._callbacks.add(e),()=>{this._callbacks.delete(e)}}constructor(e,t){this._loadFn=e,this._opts=t,this._callbacks=new Set,this._delay=null,this._timeout=null,this.retry()}}function s(e){return function(e,t){let r=Object.assign({loader:null,loading:null,delay:200,timeout:null,webpack:null,modules:null},t),i=null;function c(){if(!i){let t=new l(e,r);i={getCurrentValue:t.getCurrentValue.bind(t),subscribe:t.subscribe.bind(t),retry:t.retry.bind(t),promise:t.promise.bind(t)}}return i.promise()}if(!u){let e=r.webpack?r.webpack():r.modules;e&&o.push(t=>{for(let r of e)if(t.includes(r))return c()})}function s(e,t){!function(){c();let e=n.default.useContext(a.LoadableContext);e&&Array.isArray(r.modules)&&r.modules.forEach(t=>{e(t)})}();let o=n.default.useSyncExternalStore(i.subscribe,i.getCurrentValue,i.getCurrentValue);return n.default.useImperativeHandle(t,()=>({retry:i.retry}),[]),n.default.useMemo(()=>{var t;return o.loading||o.error?n.default.createElement(r.loading,{isLoading:o.loading,pastDelay:o.pastDelay,timedOut:o.timedOut,error:o.error,retry:i.retry}):o.loaded?n.default.createElement((t=o.loaded)&&t.default?t.default:t,e):null},[e,o])}return s.preload=()=>c(),s.displayName="LoadableComponent",n.default.forwardRef(s)}(c,e)}function f(e,t){let r=[];for(;e.length;){let n=e.pop();r.push(n(t))}return Promise.all(r).then(()=>{if(e.length)return f(e,t)})}s.preloadAll=()=>new Promise((e,t)=>{f(i).then(e,t)}),s.preloadReady=e=>(void 0===e&&(e=[]),new Promise(t=>{let r=()=>(u=!0,t());f(o,e).then(r,r)})),window.__NEXT_PRELOADREADY=s.preloadReady;let d=s},5218:function(e,t,r){e.exports=r(5095)},4623:function(e,t,r){"use strict";var n=this&&this.__read||function(e,t){var r="function"==typeof Symbol&&e[Symbol.iterator];if(!r)return e;var n,a,i=r.call(e),o=[];try{for(;(void 0===t||t-- >0)&&!(n=i.next()).done;)o.push(n.value)}catch(e){a={error:e}}finally{try{n&&!n.done&&(r=i.return)&&r.call(i)}finally{if(a)throw a.error}}return o},a=this&&this.__spreadArray||function(e,t,r){if(r||2==arguments.length)for(var n,a=0,i=t.length;a<i;a++)!n&&a in t||(n||(n=Array.prototype.slice.call(t,0,a)),n[a]=t[a]);return e.concat(n||Array.prototype.slice.call(t))};Object.defineProperty(t,"__esModule",{value:!0}),t.AsyncContract=void 0;var i=r(5594),o=r(6898);t.AsyncContract=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r=e.length-1,u=e.slice(0,r),c=e[r];return{enforce:function(e){return function(){for(var t=[],r=0;r<arguments.length;r++)t[r]=arguments[r];if(t.length<u.length){var l="Expected ".concat(u.length," arguments but only received ").concat(t.length),s=o.FAILURE.ARGUMENT_INCORRECT(l);throw new i.ValidationError(s)}for(var f=0;f<u.length;f++)u[f].check(t[f]);var d=e.apply(void 0,a([],n(t),!1));if(!(d instanceof Promise)){var l="Expected function to return a promise, but instead got ".concat(d),s=o.FAILURE.RETURN_INCORRECT(l);throw new i.ValidationError(s)}return d.then(c.check)}}}}},8133:function(e,t,r){"use strict";var n=this&&this.__read||function(e,t){var r="function"==typeof Symbol&&e[Symbol.iterator];if(!r)return e;var n,a,i=r.call(e),o=[];try{for(;(void 0===t||t-- >0)&&!(n=i.next()).done;)o.push(n.value)}catch(e){a={error:e}}finally{try{n&&!n.done&&(r=i.return)&&r.call(i)}finally{if(a)throw a.error}}return o},a=this&&this.__spreadArray||function(e,t,r){if(r||2==arguments.length)for(var n,a=0,i=t.length;a<i;a++)!n&&a in t||(n||(n=Array.prototype.slice.call(t,0,a)),n[a]=t[a]);return e.concat(n||Array.prototype.slice.call(t))};Object.defineProperty(t,"__esModule",{value:!0}),t.Contract=void 0;var i=r(5594),o=r(6898);t.Contract=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r=e.length-1,u=e.slice(0,r),c=e[r];return{enforce:function(e){return function(){for(var t=[],r=0;r<arguments.length;r++)t[r]=arguments[r];if(t.length<u.length){var l="Expected ".concat(u.length," arguments but only received ").concat(t.length),s=o.FAILURE.ARGUMENT_INCORRECT(l);throw new i.ValidationError(s)}for(var f=0;f<u.length;f++)u[f].check(t[f]);return c.check(e.apply(void 0,a([],n(t),!1)))}}}}},9443:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.checked=t.check=void 0;var n=r(5594),a=r(6898),i=new WeakMap;t.check=function(e,t,r){var n=i.get(e)||new Map;i.set(e,n);var a=n.get(t)||[];n.set(t,a),a.push(r)},t.checked=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];if(0===e.length)throw Error("No runtype provided to `@checked`. Please remove the decorator.");return function(t,r,o){var u=o.value,c=(t.name||t.constructor.name+".prototype")+("string"==typeof r?'["'.concat(r,'"]'):"[".concat(String(r),"]")),l=function(e,t,r){var n=i.get(e),a=n&&n.get(t);if(a)return a;for(var o=[],u=0;u<r;u++)o.push(u);return o}(t,r,e.length);if(l.length!==e.length)throw Error("Number of `@checked` runtypes and @check parameters not matched.");if(l.length>u.length)throw Error("Number of `@checked` runtypes exceeds actual parameter length.");o.value=function(){for(var t=[],r=0;r<arguments.length;r++)t[r]=arguments[r];return e.forEach(function(e,r){var i=l[r],o=e.validate(t[i]);if(!o.success){var u="".concat(c,", argument #").concat(i,": ").concat(o.message),s=a.FAILURE.ARGUMENT_INCORRECT(u);throw new n.ValidationError(s)}}),u.apply(this,t)}}}},5594:function(e,t){"use strict";var r,n=this&&this.__extends||(r=function(e,t){return(r=Object.setPrototypeOf||({__proto__:[]})instanceof Array&&function(e,t){e.__proto__=t}||function(e,t){for(var r in t)Object.prototype.hasOwnProperty.call(t,r)&&(e[r]=t[r])})(e,t)},function(e,t){if("function"!=typeof t&&null!==t)throw TypeError("Class extends value "+String(t)+" is not a constructor or null");function n(){this.constructor=e}r(e,t),e.prototype=null===t?Object.create(t):(n.prototype=t.prototype,new n)});Object.defineProperty(t,"__esModule",{value:!0}),t.ValidationError=void 0;var a=function(e){function t(r){var n=e.call(this,r.message)||this;return n.name="ValidationError",n.code=r.code,void 0!==r.details&&(n.details=r.details),Object.setPrototypeOf(n,t.prototype),n}return n(t,e),t}(Error);t.ValidationError=a},4690:function(e,t,r){"use strict";var n=this&&this.__createBinding||(Object.create?function(e,t,r,n){void 0===n&&(n=r),Object.defineProperty(e,n,{enumerable:!0,get:function(){return t[r]}})}:function(e,t,r,n){void 0===n&&(n=r),e[n]=t[r]}),a=this&&this.__exportStar||function(e,t){for(var r in e)"default"===r||Object.prototype.hasOwnProperty.call(t,r)||n(t,e,r)};Object.defineProperty(t,"__esModule",{value:!0}),t.InstanceOf=t.Nullish=t.Null=t.Undefined=t.Literal=void 0,a(r(924),t),a(r(111),t),a(r(8133),t),a(r(4623),t),a(r(4155),t),a(r(5594),t),a(r(4967),t),a(r(462),t),a(r(2390),t);var i=r(9319);Object.defineProperty(t,"Literal",{enumerable:!0,get:function(){return i.Literal}}),Object.defineProperty(t,"Undefined",{enumerable:!0,get:function(){return i.Undefined}}),Object.defineProperty(t,"Null",{enumerable:!0,get:function(){return i.Null}}),Object.defineProperty(t,"Nullish",{enumerable:!0,get:function(){return i.Nullish}}),a(r(6278),t),a(r(8070),t),a(r(1631),t),a(r(8691),t),a(r(2381),t),a(r(9466),t),a(r(5090),t),a(r(7137),t),a(r(9514),t),a(r(3943),t),a(r(483),t),a(r(9947),t),a(r(3479),t),a(r(5107),t);var o=r(4463);Object.defineProperty(t,"InstanceOf",{enumerable:!0,get:function(){return o.InstanceOf}}),a(r(4749),t),a(r(7896),t),a(r(7027),t),a(r(9443),t)},4155:function(e,t){"use strict";var r=this&&this.__values||function(e){var t="function"==typeof Symbol&&Symbol.iterator,r=t&&e[t],n=0;if(r)return r.call(e);if(e&&"number"==typeof e.length)return{next:function(){return e&&n>=e.length&&(e=void 0),{value:e&&e[n++],done:!e}}};throw TypeError(t?"Object is not iterable.":"Symbol.iterator is not defined.")},n=this&&this.__read||function(e,t){var r="function"==typeof Symbol&&e[Symbol.iterator];if(!r)return e;var n,a,i=r.call(e),o=[];try{for(;(void 0===t||t-- >0)&&!(n=i.next()).done;)o.push(n.value)}catch(e){a={error:e}}finally{try{n&&!n.done&&(r=i.return)&&r.call(i)}finally{if(a)throw a.error}}return o};Object.defineProperty(t,"__esModule",{value:!0}),t.when=t.match=void 0,t.match=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];return function(t){var a,i;try{for(var o=r(e),u=o.next();!u.done;u=o.next()){var c=n(u.value,2),l=c[0],s=c[1];if(l.guard(t))return s(t)}}catch(e){a={error:e}}finally{try{u&&!u.done&&(i=o.return)&&i.call(o)}finally{if(a)throw a.error}}throw Error("No alternatives were matched")}},t.when=function(e,t){return[e,t]}},924:function(e,t){"use strict";Object.defineProperty(t,"__esModule",{value:!0})},111:function(e,t){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Failcode=void 0,t.Failcode={TYPE_INCORRECT:"TYPE_INCORRECT",VALUE_INCORRECT:"VALUE_INCORRECT",KEY_INCORRECT:"KEY_INCORRECT",CONTENT_INCORRECT:"CONTENT_INCORRECT",ARGUMENT_INCORRECT:"ARGUMENT_INCORRECT",RETURN_INCORRECT:"RETURN_INCORRECT",CONSTRAINT_FAILED:"CONSTRAINT_FAILED",PROPERTY_MISSING:"PROPERTY_MISSING",PROPERTY_PRESENT:"PROPERTY_PRESENT",NOTHING_EXPECTED:"NOTHING_EXPECTED"}},7882:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.innerValidate=t.create=t.isRuntype=void 0;var n=r(4690),a=r(9587),i=r(5594),o=r(6898),u=Symbol();t.isRuntype=function(e){return(0,o.hasKey)(u,e)},t.create=function(e,t){return t[u]=!0,t.check=r,t.assert=r,t._innerValidate=function(r,n){return n.has(r,t)?(0,o.SUCCESS)(r):e(r,n)},t.validate=function(e){var r,n;return t._innerValidate(e,(r=new WeakMap,n=function(e,t){if(null!==e&&"object"==typeof e){var n=r.get(e);r.set(e,n?n.set(t,!0):new WeakMap().set(t,!0))}},{has:function(e,t){var a=r.get(e),i=a&&a.get(t)||!1;return n(e,t),i}}))},t.guard=function(e){return t.validate(e).success},t.Or=function(e){return(0,n.Union)(t,e)},t.And=function(e){return(0,n.Intersect)(t,e)},t.optional=function(){return(0,n.Optional)(t)},t.nullable=function(){return(0,n.Union)(t,n.Null)},t.withConstraint=function(e,r){return(0,n.Constraint)(t,e,r)},t.withGuard=function(e,r){return(0,n.Constraint)(t,e,r)},t.withBrand=function(e){return(0,n.Brand)(e,t)},t.reflect=t,t.toString=function(){return"Runtype<".concat((0,a.default)(t),">")},t;function r(e){var r=t.validate(e);if(r.success)return r.value;throw new i.ValidationError(r)}},t.innerValidate=function(e,t,r){return e._innerValidate(t,r)}},9587:function(e,t){"use strict";Object.defineProperty(t,"__esModule",{value:!0});var r=function(e){return function(t){switch(t.tag){case"literal":return'"'.concat(String(t.value),'"');case"string":return"string";case"brand":return t.brand;case"constraint":return t.name||r(e)(t.underlying);case"union":return t.alternatives.map(r(e)).join(" | ");case"intersect":return t.intersectees.map(r(e)).join(" & ")}return"`${".concat(a(!1,e)(t),"}`")}},n=function(e){return function(t){switch(t.tag){case"literal":return String(t.value);case"brand":return"${".concat(t.brand,"}");case"constraint":return t.name?"${".concat(t.name,"}"):n(e)(t.underlying);case"union":if(1===t.alternatives.length){var r=t.alternatives[0];return n(e)(r.reflect)}break;case"intersect":if(1===t.intersectees.length){var r=t.intersectees[0];return n(e)(r.reflect)}}return"${".concat(a(!1,e)(t),"}")}},a=function(e,t){return function(o){var u=function(t){return e?"(".concat(t,")"):t};if(t.has(o))return u("CIRCULAR ".concat(o.tag));t.add(o);try{switch(o.tag){case"unknown":case"never":case"void":case"boolean":case"number":case"bigint":case"string":case"symbol":case"function":return o.tag;case"literal":var c=o.value;return"string"==typeof c?'"'.concat(c,'"'):String(c);case"template":if(0===o.strings.length)return'""';if(1===o.strings.length)return'"'.concat(o.strings[0],'"');if(2===o.strings.length&&o.strings.every(function(e){return""===e})){var l=o.runtypes[0];return r(t)(l.reflect)}var s=!1,f=o.strings.reduce(function(e,r,a){var i=e+r,u=o.runtypes[a];if(!u)return i;var c=n(t)(u.reflect);return!s&&c.startsWith("$")&&(s=!0),i+c},"");return s?"`".concat(f,"`"):'"'.concat(f,'"');case"array":return"".concat(i(o)).concat(a(!0,t)(o.element),"[]");case"dictionary":return"{ [_: ".concat(o.key,"]: ").concat(a(!1,t)(o.value)," }");case"record":var d=Object.keys(o.fields);return d.length?"{ ".concat(d.map(function(e){var r,n;return"".concat(i(o)).concat(e).concat((r=o.isPartial,n=o.fields,r||void 0!==e&&"optional"===n[e].tag?"?":""),": ").concat("optional"===o.fields[e].tag?a(!1,t)(o.fields[e].underlying):a(!1,t)(o.fields[e]),";")}).join(" ")," }"):"{}";case"tuple":return"[".concat(o.components.map(a(!1,t)).join(", "),"]");case"union":return u("".concat(o.alternatives.map(a(!0,t)).join(" | ")));case"intersect":return u("".concat(o.intersectees.map(a(!0,t)).join(" & ")));case"optional":return a(e,t)(o.underlying)+" | undefined";case"constraint":return o.name||a(e,t)(o.underlying);case"instanceof":return o.ctor.name;case"brand":return a(e,t)(o.entity)}}finally{t.delete(o)}throw Error("impossible")}};function i(e){return e.isReadonly?"readonly ":""}t.default=a(!1,new Set)},5090:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Array=void 0;var n=r(7882),a=r(6898);t.Array=function(e){return function e(t,r){var i,o={tag:"array",isReadonly:r,element:t};return(i=(0,n.create)(function(e,r){if(!Array.isArray(e))return a.FAILURE.TYPE_INCORRECT(o,e);var i=(0,a.enumerableKeysOf)(e),u=i.map(function(a){return(0,n.innerValidate)(t,e[a],r)}),c=i.reduce(function(e,t){var r=u[t];return r.success||(e[t]=r.details||r.message),e},[]);return 0!==(0,a.enumerableKeysOf)(c).length?a.FAILURE.CONTENT_INCORRECT(o,c):(0,a.SUCCESS)(e)},o)).asReadonly=function(){return e(i.element,!0)},i}(e,!1)}},8691:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.BigInt=void 0;var n=r(7882),a=r(6898),i={tag:"bigint"};t.BigInt=(0,n.create)(function(e){return"bigint"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},i)},8070:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Boolean=void 0;var n=r(7882),a=r(6898),i={tag:"boolean"};t.Boolean=(0,n.create)(function(e){return"boolean"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},i)},7027:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Brand=void 0;var n=r(7882);t.Brand=function(e,t){return(0,n.create)(function(e){return t.validate(e)},{tag:"brand",brand:e,entity:t})}},7896:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Guard=t.Constraint=void 0;var n=r(7882),a=r(6898),i=r(4967);t.Constraint=function(e,t,r){var i={tag:"constraint",underlying:e,constraint:t,name:r&&r.name,args:r&&r.args};return(0,n.create)(function(r){var n=e.validate(r);if(!n.success)return n;var o=t(n.value);return"string"==typeof o?a.FAILURE.CONSTRAINT_FAILED(i,o):o?(0,a.SUCCESS)(n.value):a.FAILURE.CONSTRAINT_FAILED(i)},i)},t.Guard=function(e,t){return i.Unknown.withGuard(e,t)}},3943:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Dictionary=void 0;var n=r(7882),a=r(2381),i=r(7896),o=r(9587),u=r(6898),c=(0,i.Constraint)(a.String,function(e){return!isNaN(+e)},{name:"number"});t.Dictionary=function(e,t){var r=void 0===t?a.String:"string"===t?a.String:"number"===t?c:t,i=(0,o.default)(r),l={tag:"dictionary",key:i,value:e};return(0,n.create)(function(t,a){if(null==t||"object"!=typeof t||Object.getPrototypeOf(t)!==Object.prototype&&(!Array.isArray(t)||"string"===i))return u.FAILURE.TYPE_INCORRECT(l,t);var o=/^(?:NaN|-?\d+(?:\.\d+)?)$/,c=(0,u.enumerableKeysOf)(t),s=c.reduce(function(i,c){var s="string"==typeof c&&o.test(c),f=s?globalThis.Number(c):c;return(s?r.guard(f)||r.guard(c):r.guard(f))?i[c]=(0,n.innerValidate)(e,t[c],a):i[c]=u.FAILURE.KEY_INCORRECT(l,r.reflect,f),i},{}),f=c.reduce(function(e,t){var r=s[t];return r.success||(e[t]=r.details||r.message),e},{});return 0!==(0,u.enumerableKeysOf)(f).length?u.FAILURE.CONTENT_INCORRECT(l,f):(0,u.SUCCESS)(t)},l)}},5107:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Function=void 0;var n=r(7882),a=r(6898),i={tag:"function"};t.Function=(0,n.create)(function(e){return"function"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},i)},4463:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.InstanceOf=void 0;var n=r(7882),a=r(6898);t.InstanceOf=function(e){var t={tag:"instanceof",ctor:e};return(0,n.create)(function(r){return r instanceof e?(0,a.SUCCESS)(r):a.FAILURE.TYPE_INCORRECT(t,r)},t)}},9947:function(e,t,r){"use strict";var n=this&&this.__values||function(e){var t="function"==typeof Symbol&&Symbol.iterator,r=t&&e[t],n=0;if(r)return r.call(e);if(e&&"number"==typeof e.length)return{next:function(){return e&&n>=e.length&&(e=void 0),{value:e&&e[n++],done:!e}}};throw TypeError(t?"Object is not iterable.":"Symbol.iterator is not defined.")};Object.defineProperty(t,"__esModule",{value:!0}),t.Intersect=void 0;var a=r(7882),i=r(6898);t.Intersect=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];return(0,a.create)(function(t,r){var o,u;try{for(var c=n(e),l=c.next();!l.done;l=c.next()){var s=l.value,f=(0,a.innerValidate)(s,t,r);if(!f.success)return f}}catch(e){o={error:e}}finally{try{l&&!l.done&&(u=c.return)&&u.call(c)}finally{if(o)throw o.error}}return(0,i.SUCCESS)(t)},{tag:"intersect",intersectees:e})}},4749:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Lazy=void 0;var n=r(7882);t.Lazy=function(e){var t,r={get tag(){return a().tag}};function a(){if(!t)for(var n in t=e())"tag"!==n&&(r[n]=t[n]);return t}return(0,n.create)(function(e){return a().validate(e)},r)}},9319:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Nullish=t.Null=t.Undefined=t.Literal=t.literal=void 0;var n=r(7882),a=r(6898),i=r(483);function o(e){return Array.isArray(e)?String(e.map(String)):"bigint"==typeof e?String(e)+"n":String(e)}function u(e){return(0,n.create)(function(t){return t===e?(0,a.SUCCESS)(t):a.FAILURE.VALUE_INCORRECT("literal","`".concat(o(e),"`"),"`".concat(o(t),"`"))},{tag:"literal",value:e})}t.literal=o,t.Literal=u,t.Undefined=u(void 0),t.Null=u(null),t.Nullish=(0,i.Union)(t.Null,t.Undefined)},462:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Never=void 0;var n=r(7882),a=r(6898);t.Never=(0,n.create)(a.FAILURE.NOTHING_EXPECTED,{tag:"never"})},1631:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Number=void 0;var n=r(7882),a=r(6898),i={tag:"number"};t.Number=(0,n.create)(function(e){return"number"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},i)},3479:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Optional=void 0;var n=r(7882),a=r(6898);t.Optional=function(e){return(0,n.create)(function(t){return void 0===t?(0,a.SUCCESS)(t):e.validate(t)},{tag:"optional",underlying:e})}},9514:function(e,t,r){"use strict";var n=this&&this.__read||function(e,t){var r="function"==typeof Symbol&&e[Symbol.iterator];if(!r)return e;var n,a,i=r.call(e),o=[];try{for(;(void 0===t||t-- >0)&&!(n=i.next()).done;)o.push(n.value)}catch(e){a={error:e}}finally{try{n&&!n.done&&(r=i.return)&&r.call(i)}finally{if(a)throw a.error}}return o},a=this&&this.__spreadArray||function(e,t,r){if(r||2==arguments.length)for(var n,a=0,i=t.length;a<i;a++)!n&&a in t||(n||(n=Array.prototype.slice.call(t,0,a)),n[a]=t[a]);return e.concat(n||Array.prototype.slice.call(t))};Object.defineProperty(t,"__esModule",{value:!0}),t.Partial=t.Record=t.InternalRecord=void 0;var i=r(7882),o=r(6898);function u(e,t,r){var c,l={tag:"record",isPartial:t,isReadonly:r,fields:e};return(c=(0,i.create)(function(r,u){if(null==r)return o.FAILURE.TYPE_INCORRECT(l,r);var c=(0,o.enumerableKeysOf)(e);if(0!==c.length&&"object"!=typeof r)return o.FAILURE.TYPE_INCORRECT(l,r);var s=a([],n(new Set(a(a([],n(c),!1),n((0,o.enumerableKeysOf)(r)),!1))),!1),f=s.reduce(function(n,a){var c=(0,o.hasKey)(a,e),l=(0,o.hasKey)(a,r);if(c){var s=e[a],f=t||"optional"===s.reflect.tag;if(l){var d=r[a];f&&void 0===d?n[a]=(0,o.SUCCESS)(d):n[a]=(0,i.innerValidate)(s,d,u)}else f?n[a]=(0,o.SUCCESS)(void 0):n[a]=o.FAILURE.PROPERTY_MISSING(s.reflect)}else if(l){var d=r[a];n[a]=(0,o.SUCCESS)(d)}else throw Error("impossible");return n},{}),d=s.reduce(function(e,t){var r=f[t];return r.success||(e[t]=r.details||r.message),e},{});return 0!==(0,o.enumerableKeysOf)(d).length?o.FAILURE.CONTENT_INCORRECT(l,d):(0,o.SUCCESS)(r)},l)).asPartial=function(){return u(c.fields,!0,c.isReadonly)},c.asReadonly=function(){return u(c.fields,c.isPartial,!0)},c.pick=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r={};return e.forEach(function(e){r[e]=c.fields[e]}),u(r,c.isPartial,c.isReadonly)},c.omit=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r={};return(0,o.enumerableKeysOf)(c.fields).forEach(function(t){e.includes(t)||(r[t]=c.fields[t])}),u(r,c.isPartial,c.isReadonly)},c.extend=function(e){return u(Object.assign({},c.fields,e),c.isPartial,c.isReadonly)},c}t.InternalRecord=u,t.Record=function(e){return u(e,!1,!1)},t.Partial=function(e){return u(e,!0,!1)}},2381:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.String=void 0;var n=r(7882),a=r(6898),i={tag:"string"};t.String=(0,n.create)(function(e){return"string"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},i)},9466:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Symbol=void 0;var n=r(7882),a=r(6898),i={tag:"symbol"};t.Symbol=(0,n.create)(function(e){return"symbol"==typeof e?(0,a.SUCCESS)(e):a.FAILURE.TYPE_INCORRECT(i,e)},Object.assign(function(e){var t={tag:"symbol",key:e};return(0,n.create)(function(r){if("symbol"!=typeof r)return a.FAILURE.TYPE_INCORRECT(t,r);var n=globalThis.Symbol.keyFor(r);return n!==e?a.FAILURE.VALUE_INCORRECT("symbol key",o(e),o(n)):(0,a.SUCCESS)(r)},t)},i));var o=function(e){return void 0===e?"undefined":'"'.concat(e,'"')}},6278:function(e,t,r){"use strict";var n=this&&this.__read||function(e,t){var r="function"==typeof Symbol&&e[Symbol.iterator];if(!r)return e;var n,a,i=r.call(e),o=[];try{for(;(void 0===t||t-- >0)&&!(n=i.next()).done;)o.push(n.value)}catch(e){a={error:e}}finally{try{n&&!n.done&&(r=i.return)&&r.call(i)}finally{if(a)throw a.error}}return o},a=this&&this.__spreadArray||function(e,t,r){if(r||2==arguments.length)for(var n,a=0,i=t.length;a<i;a++)!n&&a in t||(n||(n=Array.prototype.slice.call(t,0,a)),n[a]=t[a]);return e.concat(n||Array.prototype.slice.call(t))},i=this&&this.__values||function(e){var t="function"==typeof Symbol&&Symbol.iterator,r=t&&e[t],n=0;if(r)return r.call(e);if(e&&"number"==typeof e.length)return{next:function(){return e&&n>=e.length&&(e=void 0),{value:e&&e[n++],done:!e}}};throw TypeError(t?"Object is not iterable.":"Symbol.iterator is not defined.")};Object.defineProperty(t,"__esModule",{value:!0}),t.Template=void 0;var o=r(7882),u=r(9587),c=r(6898),l=r(9319),s=function(e){return e.replace(/[.*+?^${}()|[\]\\]/g,"\\$&")},f=function(e){if(0<e.length&&Array.isArray(e[0])){var t=n(e),r=t[0],a=t.slice(1);return[Array.from(r),a]}var r=e.reduce(function(e,t){return(0,o.isRuntype)(t)?e.push(""):e.push(e.pop()+String(t)),e},[""]),a=e.filter(o.isRuntype);return[r,a]},d=function(e,t){for(var r=0;r<t.length;)switch(t[r].reflect.tag){case"literal":var i=t[r];t.splice(r,1);var o=String(i.value);e.splice(r,2,e[r]+o+e[r+1]);break;case"template":var u=t[r];t.splice.apply(t,a([r,1],n(u.runtypes),!1));var c=u.strings;if(1===c.length)e.splice(r,2,e[r]+c[0]+e[r+1]);else{var l=c[0],s=c.slice(1,-1),f=c[c.length-1];e.splice.apply(e,a(a([r,2,e[r]+l],n(s),!1),[f+e[r+1]],!1))}break;case"union":var d=t[r];if(1===d.alternatives.length)try{var v=y(d);t.splice(r,1);var o=String(v.value);e.splice(r,2,e[r]+o+e[r+1]);break}catch(e){r++;break}else{r++;break}case"intersect":var p=t[r];if(1===p.intersectees.length)try{var h=y(p);t.splice(r,1);var o=String(h.value);e.splice(r,2,e[r]+o+e[r+1]);break}catch(e){r++;break}else{r++;break}default:r++}},v=function(e){var t=n(f(e),2),r=t[0],a=t[1];return d(r,a),[r,a]},y=function(e){switch(e.reflect.tag){case"literal":return e;case"brand":return y(e.reflect.entity);case"union":if(1===e.reflect.alternatives.length)return y(e.reflect.alternatives[0]);break;case"intersect":if(1===e.reflect.intersectees.length)return y(e.reflect.intersectees[0])}throw void 0},p=function(e){return e},h={string:[function(e){return globalThis.String(e)},".*"],number:[function(e){return globalThis.Number(e)},"[+-]?(?:\\d*\\.\\d+|\\d+\\.\\d*|\\d+)(?:[Ee][+-]?\\d+)?","0[Bb][01]+","0[Oo][0-7]+","0[Xx][0-9A-Fa-f]+"],bigint:[function(e){return globalThis.BigInt(e)},"-?[1-9]d*"],boolean:[function(e){return"false"!==e},"true","false"],null:[function(){return null},"null"],undefined:[function(){},"undefined"]},E=function(e){switch(e.tag){case"literal":return n(h[(0,c.typeOf)(e.value)]||[p],1)[0];case"brand":return E(e.entity);case"constraint":return E(e.underlying);case"union":return e.alternatives.map(E);case"intersect":return e.intersectees.map(E);default:return n(h[e.tag]||[p],1)[0]}},g=function(e,t){return function(r){var n,a,u,s,f=E(e);if(Array.isArray(f))switch(e.tag){case"union":try{for(var d=i(e.alternatives),v=d.next();!v.done;v=d.next()){var y=v.value,p=g(y.reflect,t)(r);if(p.success)return p}}catch(e){n={error:e}}finally{try{v&&!v.done&&(a=d.return)&&a.call(d)}finally{if(n)throw n.error}}return c.FAILURE.TYPE_INCORRECT(e,r);case"intersect":try{for(var h=i(e.intersectees),_=h.next();!_.done;_=h.next()){var b=_.value,p=g(b.reflect,t)(r);if(!p.success)return p}}catch(e){u={error:e}}finally{try{_&&!_.done&&(s=h.return)&&s.call(h)}finally{if(u)throw u.error}}return(0,c.SUCCESS)(r);default:throw Error("impossible")}else{var p=(0,o.innerValidate)(e,f(r),t);return p.success||"VALUE_INCORRECT"!==p.code||"literal"!==e.tag?p:c.FAILURE.VALUE_INCORRECT("literal",'"'.concat((0,l.literal)(e.value),'"'),'"'.concat(r,'"'))}}},_=function(e){switch(e.tag){case"literal":return s(String(e.value));case"brand":return _(e.entity);case"constraint":return _(e.underlying);case"union":return e.alternatives.map(_).join("|");case"template":return e.strings.map(s).reduce(function(t,r,n){var a=t+r,i=e.runtypes[n];return i?a+"(?:".concat(_(i.reflect),")"):a},"");default:return n(h[e.tag]||[void 0,".*"]).slice(1).join("|")}},b=function(e){var t=e.strings.map(s).reduce(function(t,r,n){var a=t+r,i=e.runtypes[n];return i?a+"(".concat(_(i.reflect),")"):a},"");return RegExp("^".concat(t,"$"),"su")};t.Template=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r=n(v(e),2),a=r[0],i=r[1],s={tag:"template",strings:a,runtypes:i},f=b(s),d=function(e,t){var r=e.match(f);if(!r)return c.FAILURE.VALUE_INCORRECT("string","".concat((0,u.default)(s)),'"'.concat((0,l.literal)(e),'"'));for(var n=r.slice(1),a=0;a<i.length;a++){var o=i[a],d=n[a],v=g(o.reflect,t)(d);if(!v.success)return v}return(0,c.SUCCESS)(e)};return(0,o.create)(function(e,t){if("string"!=typeof e)return c.FAILURE.TYPE_INCORRECT(s,e);var r=d(e,t);if(r.success)return(0,c.SUCCESS)(e);var n=c.FAILURE.VALUE_INCORRECT("string","".concat((0,u.default)(s)),'"'.concat(e,'"'));return n.message!==r.message&&(n.message+=" (inner: ".concat(r.message,")")),n},s)}},7137:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Tuple=void 0;var n=r(7882),a=r(6898);t.Tuple=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r={tag:"tuple",components:e};return(0,n.create)(function(t,i){if(!Array.isArray(t))return a.FAILURE.TYPE_INCORRECT(r,t);if(t.length!==e.length)return a.FAILURE.CONSTRAINT_FAILED(r,"Expected length ".concat(e.length,", but was ").concat(t.length));var o=(0,a.enumerableKeysOf)(t),u=o.map(function(r){return(0,n.innerValidate)(e[r],t[r],i)}),c=o.reduce(function(e,t){var r=u[t];return r.success||(e[t]=r.details||r.message),e},[]);return 0!==(0,a.enumerableKeysOf)(c).length?a.FAILURE.CONTENT_INCORRECT(r,c):(0,a.SUCCESS)(t)},r)}},483:function(e,t,r){"use strict";var n=this&&this.__values||function(e){var t="function"==typeof Symbol&&Symbol.iterator,r=t&&e[t],n=0;if(r)return r.call(e);if(e&&"number"==typeof e.length)return{next:function(){return e&&n>=e.length&&(e=void 0),{value:e&&e[n++],done:!e}}};throw TypeError(t?"Object is not iterable.":"Symbol.iterator is not defined.")};Object.defineProperty(t,"__esModule",{value:!0}),t.Union=void 0;var a=r(7882),i=r(6898);t.Union=function(){for(var e=[],t=0;t<arguments.length;t++)e[t]=arguments[t];var r={tag:"union",alternatives:e,match:function(){for(var t=[],r=0;r<arguments.length;r++)t[r]=arguments[r];return function(r){for(var n=0;n<e.length;n++)if(e[n].guard(r))return t[n](r)}}};return(0,a.create)(function(t,o){if("object"!=typeof t||null===t){try{for(var u,c,l,s,f,d,v,y,p=n(e),h=p.next();!h.done;h=p.next()){var E=h.value;if((0,a.innerValidate)(E,t,o).success)return(0,i.SUCCESS)(t)}}catch(e){u={error:e}}finally{try{h&&!h.done&&(c=p.return)&&c.call(p)}finally{if(u)throw u.error}}return i.FAILURE.TYPE_INCORRECT(r,t)}var g={};try{for(var _=n(e),b=_.next();!b.done;b=_.next()){var E=b.value;if("record"===E.reflect.tag)for(var R in E.reflect.fields)!function(e){var t=E.reflect.fields[e];"literal"===t.tag&&(g[e]?g[e].every(function(e){return e!==t.value})&&g[e].push(t.value):g[e]=[t.value])}(R)}}catch(e){l={error:e}}finally{try{b&&!b.done&&(s=_.return)&&s.call(_)}finally{if(l)throw l.error}}for(var R in g)if(g[R].length===e.length)try{for(var C=(f=void 0,n(e)),O=C.next();!O.done;O=C.next()){var E=O.value;if("record"===E.reflect.tag){var m=E.reflect.fields[R];if("literal"===m.tag&&(0,i.hasKey)(R,t)&&t[R]===m.value)return(0,a.innerValidate)(E,t,o)}}}catch(e){f={error:e}}finally{try{O&&!O.done&&(d=C.return)&&d.call(C)}finally{if(f)throw f.error}}try{for(var T=n(e),S=T.next();!S.done;S=T.next()){var I=S.value;if((0,a.innerValidate)(I,t,o).success)return(0,i.SUCCESS)(t)}}catch(e){v={error:e}}finally{try{S&&!S.done&&(y=T.return)&&y.call(T)}finally{if(v)throw v.error}}return i.FAILURE.TYPE_INCORRECT(r,t)},r)}},4967:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Unknown=void 0;var n=r(7882),a=r(6898);t.Unknown=(0,n.create)(function(e){return(0,a.SUCCESS)(e)},{tag:"unknown"})},2390:function(e,t,r){"use strict";Object.defineProperty(t,"__esModule",{value:!0}),t.Void=void 0;var n=r(4967);t.Void=n.Unknown},6898:function(e,t,r){"use strict";var n=this&&this.__assign||function(){return(n=Object.assign||function(e){for(var t,r=1,n=arguments.length;r<n;r++)for(var a in t=arguments[r])Object.prototype.hasOwnProperty.call(t,a)&&(e[a]=t[a]);return e}).apply(this,arguments)};Object.defineProperty(t,"__esModule",{value:!0}),t.FAILURE=t.SUCCESS=t.enumerableKeysOf=t.typeOf=t.hasKey=void 0;var a=r(111),i=r(9587);t.hasKey=function(e,t){return"object"==typeof t&&null!==t&&e in t},t.typeOf=function(e){var t,r,n;return"object"==typeof e?null===e?"null":Array.isArray(e)?"array":(null===(t=e.constructor)||void 0===t?void 0:t.name)==="Object"?"object":null!==(n=null===(r=e.constructor)||void 0===r?void 0:r.name)&&void 0!==n?n:typeof e:typeof e},t.enumerableKeysOf=function(e){return"object"==typeof e&&null!==e?Reflect.ownKeys(e).filter(function(t){var r,n;return null===(n=null===(r=e.propertyIsEnumerable)||void 0===r?void 0:r.call(e,t))||void 0===n||n}):[]},t.SUCCESS=function(e){return{success:!0,value:e}},t.FAILURE=Object.assign(function(e,t,r){return n({success:!1,code:e,message:t},r?{details:r}:{})},{TYPE_INCORRECT:function(e,r){var n="Expected ".concat("template"===e.tag?"string ".concat((0,i.default)(e)):(0,i.default)(e),", but was ").concat((0,t.typeOf)(r));return(0,t.FAILURE)(a.Failcode.TYPE_INCORRECT,n)},VALUE_INCORRECT:function(e,r,n){return(0,t.FAILURE)(a.Failcode.VALUE_INCORRECT,"Expected ".concat(e," ").concat(String(r),", but was ").concat(String(n)))},KEY_INCORRECT:function(e,r,n){return(0,t.FAILURE)(a.Failcode.KEY_INCORRECT,"Expected ".concat((0,i.default)(e)," key to be ").concat((0,i.default)(r),", but was ").concat((0,t.typeOf)(n)))},CONTENT_INCORRECT:function(e,r){var n=JSON.stringify(r,null,2).replace(/^ *null,\n/gm,""),o="Validation failed:\n".concat(n,".\nObject should match ").concat((0,i.default)(e));return(0,t.FAILURE)(a.Failcode.CONTENT_INCORRECT,o,r)},ARGUMENT_INCORRECT:function(e){return(0,t.FAILURE)(a.Failcode.ARGUMENT_INCORRECT,e)},RETURN_INCORRECT:function(e){return(0,t.FAILURE)(a.Failcode.RETURN_INCORRECT,e)},CONSTRAINT_FAILED:function(e,r){return(0,t.FAILURE)(a.Failcode.CONSTRAINT_FAILED,"Failed constraint check for ".concat((0,i.default)(e)).concat(r?": ".concat(r):""))},PROPERTY_MISSING:function(e){var r="Expected ".concat((0,i.default)(e),", but was missing");return(0,t.FAILURE)(a.Failcode.PROPERTY_MISSING,r)},PROPERTY_PRESENT:function(e){var r="Expected nothing, but was ".concat((0,t.typeOf)(e));return(0,t.FAILURE)(a.Failcode.PROPERTY_PRESENT,r)},NOTHING_EXPECTED:function(e){var r="Expected nothing, but was ".concat((0,t.typeOf)(e));return(0,t.FAILURE)(a.Failcode.NOTHING_EXPECTED,r)}})}}]);