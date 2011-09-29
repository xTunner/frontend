var Prototype = {
    Browser: {
        IE: !! (window.attachEvent && navigator.userAgent.indexOf('Opera') === -1),
        Opera: navigator.userAgent.indexOf('Opera') > -1,
        WebKit: navigator.userAgent.indexOf('AppleWebKit/') > -1,
        Gecko: navigator.userAgent.indexOf('Gecko') > -1 && navigator.userAgent.indexOf('KHTML') === -1,
        MobileSafari: !! navigator.userAgent.match(/Apple.*Mobile.*Safari/)
    },
    JSONFilter: /^\/\*-secure-([\s\S]*)\*\/\s*$/,
    emptyFunction: function () {}
};
var Class = {
    create: function () {
        var parent = null,
            properties = $A(arguments);
        if (Object.isFunction(properties[0])) parent = properties.shift();

        function klass() {
            this.initialize.apply(this, arguments);
        }
        Object.extend(klass, Class.Methods);
        klass.superclass = parent;
        klass.subclasses = [];
        if (parent) {
            var subclass = function () {};
            subclass.prototype = parent.prototype;
            klass.prototype = new subclass;
            parent.subclasses.push(klass);
        }
        for (var i = 0; i < properties.length; i++)
        klass.addMethods(properties[i]);
        if (!klass.prototype.initialize) klass.prototype.initialize = Prototype.emptyFunction;
        klass.prototype.constructor = klass;
        return klass;
    }
};
Class.Methods = {
    addMethods: function (source) {
        var ancestor = this.superclass && this.superclass.prototype;
        var properties = Object.keys(source);
        if (!Object.keys({
            toString: true
        }).length) properties.push("toString", "valueOf");
        for (var i = 0, length = properties.length; i < length; i++) {
            var property = properties[i],
                value = source[property];
            if (ancestor && Object.isFunction(value) && value.argumentNames().first() == "$super") {
                var method = value;
                value = (function (m) {
                    return function () {
                        return ancestor[m].apply(this, arguments)
                    };
                })(property).wrap(method);
                value.valueOf = method.valueOf.bind(method);
                value.toString = method.toString.bind(method);
            }
            this.prototype[property] = value;
        }
        return this;
    }
};
Object.extend = function (destination, source) {
    for (property in source) {
        destination[property] = source[property];
    }
    return destination;
}
Object.extend(Object, {
    observeEvent: function (obj, type, fn) {
        if (obj.attachEvent) {
            obj["e" + type + fn] = fn;
            obj[type + fn] = function () {
                obj["e" + type + fn](window.event)
            };
            obj.attachEvent("on" + type, obj[type + fn]);
        } else {
            obj.addEventListener(type, fn, false);
        }
    },
    clone: function (object) {
        return Object.extend({}, object);
    },
    keys: function (object) {
        var keys = [];
        for (var property in object)
        keys.push(property);
        return keys;
    },
    inspect: function (object) {
        try {
            if (Object.isUndefined(object)) return 'undefined';
            if (object === null) return 'null';
            return object.inspect ? object.inspect() : String(object);
        } catch (e) {
            if (e instanceof RangeError) return '...';
            throw e;
        }
    },
    toJSON: function (object) {
        var type = typeof object;
        switch (type) {
        case 'undefined':
        case 'function':
        case 'unknown':
            return;
        case 'boolean':
            return object.toString();
        }
        if (object === null) return 'null';
        if (object.toJSON) return object.toJSON();
        if (Object.isElement(object)) return;
        var results = [];
        for (var property in object) {
            var value = Object.toJSON(object[property]);
            if (!Object.isUndefined(value)) results.push(property.toJSON() + ': ' + value);
        }
        return '{' + results.join(', ') + '}';
    },
    isArray: function (object) {
        return object != null && typeof object == "object" && 'splice' in object && 'join' in object;
    },
    isElement: function (object) {
        return !!(object && object.nodeType == 1);
    },
    isFunction: function (object) {
        return typeof object == "function";
    },
    isUndefined: function (object) {
        return typeof object == "undefined";
    }
});
Object.extend(Function.prototype, {
    argumentNames: function () {
        var names = this.toString().match(/^[\s\(]*function[^(]*\(([^\)]*)\)/)[1].replace(/\s+/g, '').split(',');
        return names.length == 1 && !names[0] ? [] : names;
    },
    bind: function () {
        if (arguments.length < 2 && Object.isUndefined(arguments[0])) return this;
        var __method = this,
            args = $A(arguments),
            object = args.shift();
        return function () {
            return __method.apply(object, args.concat($A(arguments)));
        }
    },
    wrap: function (wrapper) {
        var __method = this;
        return function () {
            return wrapper.apply(this, [__method.bind(this)].concat($A(arguments)));
        }
    }
});
Date.prototype.toJSON = function () {
    return '"' + this.getUTCFullYear() + '-' + (this.getUTCMonth() + 1).toPaddedString(2) + '-' + this.getUTCDate().toPaddedString(2) + 'T' + this.getUTCHours().toPaddedString(2) + ':' + this.getUTCMinutes().toPaddedString(2) + ':' + this.getUTCSeconds().toPaddedString(2) + 'Z"';
};
Object.extend(Number.prototype, {
    toJSON: function () {
        return isFinite(this) ? this.toString() : 'null';
    }
});
Array.prototype.iterate = function (func) {
    for (var i = 0; i < this.length; i++) func(this[i], i);
}
if (!Array.prototype.each) Array.prototype.each = Array.prototype.iterate;
Object.extend(Array.prototype, {
    clone: function () {
        return [].concat(this);
    },
    indexOf: function (item, i) {
        i || (i = 0);
        var length = this.length;
        if (i < 0) i = length + i;
        for (; i < length; i++)
        if (this[i] === item) return i;
        return -1;
    },
    toJSON: function () {
        var results = [];
        this.each(function (object) {
            var value = Object.toJSON(object);
            if (!Object.isUndefined(value)) results.push(value);
        });
        return '[' + results.join(', ') + ']';
    }
});
Object.extend(String, {
    interpret: function (value) {
        return value == null ? '' : String(value);
    },
    specialChar: {
        '\b': '\\b',
        '\t': '\\t',
        '\n': '\\n',
        '\f': '\\f',
        '\r': '\\r',
        '\\': '\\\\'
    }
});
Object.extend(String.prototype, {
    gsub: function (pattern, replacement) {
        var result = '',
            source = this,
            match;
        replacement = arguments.callee.prepareReplacement(replacement);
        while (source.length > 0) {
            if (match = source.match(pattern)) {
                result += source.slice(0, match.index);
                result += String.interpret(replacement(match));
                source = source.slice(match.index + match[0].length);
            } else {
                result += source, source = '';
            }
        }
        return result;
    },
    sub: function (pattern, replacement, count) {
        replacement = this.gsub.prepareReplacement(replacement);
        count = Object.isUndefined(count) ? 1 : count;
        return this.gsub(pattern, function (match) {
            if (--count < 0) return match[0];
            return replacement(match);
        });
    },
    strip: function () {
        return this.replace(/^\s+/, '').replace(/\s+$/, '');
    },
    inspect: function (useDoubleQuotes) {
        var escapedString = this.gsub(/[\x00-\x1f\\]/, function (match) {
            var character = String.specialChar[match[0]];
            return character ? character : '\\u00' + match[0].charCodeAt().toPaddedString(2, 16);
        });
        if (useDoubleQuotes) return '"' + escapedString.replace(/"/g, '\\"') + '"';
        return "'" + escapedString.replace(/'/g, '\\\'') + "'";
    },
    unfilterJSON: function (filter) {
        return this.sub(filter || Prototype.JSONFilter, '#{1}');
    },
    evalJSON: function (sanitize) {
        var json = this.unfilterJSON();
        try {
            if (!sanitize || json.isJSON()) return eval('(' + json + ')');
        } catch (e) {}
        throw new SyntaxError('Badly formed JSON string: ' + this.inspect());
    },
    toJSON: function () {
        return this.inspect(true);
    },
    isJSON: function () {
        var str = this;
        if (str.blank()) return false;
        str = this.replace(/\\./g, '@').replace(/"[^"\\\n\r]*"/g, '');
        return (/^[,:{}\[\]0-9.\-+Eaeflnr-u \n\r\t]*$/).test(str);
    },
    toArray: function () {
        return this.split('');
    },
    startsWith: function (pattern) {
        return this.indexOf(pattern) === 0;
    },
    endsWith: function (pattern) {
        var d = this.length - pattern.length;
        return d >= 0 && this.lastIndexOf(pattern) === d;
    },
    stripTags: function () {
        return this.replace(/<\w+(\s+("[^"]*"|'[^']*'|[^>])+)?>|<\/\w+>/gi, '');
    },
    blank: function () {
        return /^\s*$/.test(this);
    }
});
String.prototype.gsub.prepareReplacement = function (replacement) {
    if (Object.isFunction(replacement)) return replacement;
    var template = new Template(replacement);
    return function (match) {
        return template.evaluate(match)
    };
};
var Template = Class.create({
    initialize: function (template, pattern) {
        this.template = template.toString();
        this.pattern = pattern || Template.Pattern;
    },
    evaluate: function (object) {
        if (Object.isFunction(object.toTemplateReplacements)) object = object.toTemplateReplacements();
        return this.template.gsub(this.pattern, function (match) {
            if (object == null) return '';
            var before = match[1] || '';
            if (before == '\\') return match[2];
            var ctx = object,
                expr = match[3];
            var pattern = /^([^.[]+|\[((?:.*?[^\\])?)\])(\.|\[|$)/;
            match = pattern.exec(expr);
            if (match == null) return before;
            while (match != null) {
                var comp = match[1].startsWith('[') ? match[2].gsub('\\\\]', ']') : match[1];
                ctx = ctx[comp];
                if (null == ctx || '' == match[3]) break;
                expr = expr.substring('[' == match[3] ? match[1].length : match[0].length);
                match = pattern.exec(expr);
            }
            return before + String.interpret(ctx);
        });
    }
});
Template.Pattern = /(^|.|\r|\n)(#\{(.*?)\})/;

function $A(iterable) {
    if (!iterable) return [];
    if (iterable.toArray) return iterable.toArray();
    var length = iterable.length || 0;
    var results = new Array(length);
    while (length--) results[length] = iterable[length];
    return results;
}
if (Prototype.Browser.WebKit) {
    $A = function (iterable) {
        if (!iterable) return [];
        if (!(typeof iterable === 'function' && typeof iterable.length === 'number' && typeof iterable.item === 'function') && iterable.toArray) return iterable.toArray();
        var length = iterable.length || 0,
            results = new Array(length);
        while (length--) results[length] = iterable[length];
        return results;
    };
}
Array.from = $A;

function $(el) {
    if (typeof el == 'string') el = document.getElementById(el);
    if (el == null) return false;
    else return Element.extend(el);
}
if (!window.Element) var Element = new Object();
Object.extend(Element, {
    observe: function (type, fn) {
        Object.observeEvent(this, type, fn);
    },
    remove: function (element) {
        element = $(element);
        element.parentNode.removeChild(element);
        return element;
    },
    hasClassName: function (className) {
        var hasClass = false;
        this.className.split(' ').each(function (cn) {
            if (cn == className) hasClass = true;
        });
        return hasClass;
    },
    hasClassNameInternal: function (element, className) {
        element = $(element);
        if (!element) return;
        var hasClass = false;
        element.className.split(' ').each(function (cn) {
            if (cn == className) hasClass = true;
        });
        return hasClass;
    },
    addClassName: function (className) {
        this.removeClassName(className);
        var safeClassName = new String(this.className);
        this.className += (safeClassName.blank()) ? className : ' ' + className;
    },
    removeClassName: function (className) {
        var currentClassName = this.className;
        var classNameArray = currentClassName.split(' ');
        var newClassName = '';
        for (var i = 0; i < classNameArray.length; i++) {
            var cleanClassName = classNameArray[i].strip();
            if (cleanClassName != className) {
                if (newClassName != '') newClassName += ' ';
                newClassName += cleanClassName;
            }
        }
        this.className = newClassName;
    },
    extend: function (object) {
        return Object.extend(object, Element);
    }
});
var Selector = {
    findElementsByTagAndClass: function (classname, tagname, root) {
        if (!root) root = document;
        else if (typeof root == "string") root = $(root);
        if (!tagname) tagname = "*";
        var all = root.getElementsByTagName(tagname);
        if (!classname) return all;
        var elements = [];
        for (var i = 0; i < all.length; i++) {
            var element = all[i];
            if (this.isMember(element, classname)) {
                elements.push(element);
            }
        }
        return elements;
    },
    isMember: function (element, classname) {
        var classes = element.className;
        if (!classes) return false;
        if (classes == classname) return true;
        var whitespace = /\s+/;
        if (!whitespace.test(classes)) return false;
        var c = classes.split(whitespace);
        for (var i = 0; i < c.length; i++) {
            if (c[i] == classname) return true;
        }
        return false;
    },
    findElementsByClassName: function (tagAndClass) {
        var splitTagAndClass = tagAndClass.split('.');
        var tag = (splitTagAndClass[0] == '') ? '*' : splitTagAndClass[0];
        var className = splitTagAndClass[1];
        var elements = new Array();
        var tags = Selector.findElementsByTagName(tag);
        for (var i = 0; i < tags.length; i++) {
            if (Element.hasClassNameInternal(tags[i], className)) {
                elements.push(tags[i]);
            }
        };
        return elements;
    },
    findElementsByTagName: function (tag) {
        return document.getElementsByTagName(tag);
    },
    findElementById: function (id) {
        var id = id.replace('#', '');
        return $(id);
    }
}

function $$() {
    var elements = new Array();
    for (var i = 0; i < arguments.length; i++) {
        var arg = arguments[i];
        if (arg.indexOf('#') > -1) {
            elements.push(Selector.findElementById(arg));
        } else if (arg.indexOf('.') > -1) {
            var foundElements = Selector.findElementsByClassName(arg);
            for (var j = 0; j < foundElements.length; j++) {
                elements.push(Element.extend(foundElements[j]));
            }
        } else {
            var foundElements = Selector.findElementsByTagName(arg);
            for (var j = 0; j < foundElements.length; j++) {
                elements.push(Element.extend(foundElements[j]));
            }
        }
    }
    return elements;
}
var Ajax = {
    _request: null,
    _requestObjects: [function () {
        return new XMLHttpRequest()
    }, function () {
        return new ActiveXObject('Msxml2.XMLHTTP')
    }, function () {
        return new ActiveXObject('Microsoft.XMLHTTP')
    }],
    initOptions: function (options) {
        this.options = {
            method: 'POST',
            asynchronous: true,
            contentType: 'application/x-www-form-urlencoded',
            encoding: 'UTF-8',
            parameters: '',
            evalJSON: true,
            evalJS: true
        };
        for (option in options) {
            this.options[option] = options[option];
        }
    },
    getRequestObj: function () {
        if (this._request != null) return this._request;
        for (var i = 0; i < this._requestObjects.length; i++) {
            try {
                var requestFunc = this._requestObjects[i];
                var request = requestFunc();
                if (request != null) {
                    this._request = requestFunc;
                    return request;
                }
            } catch (e) {
                continue;
            }
        }
        throw new Error("XMLHttpRequest Not Supported");
    },
    Request: function (url, options) {
        var request = this.getRequestObj();
        this.initOptions(options);
        request.onreadystatechange = function () {
            if (request.readyState == 4) {
                if (request.status == 200) {
                    options.onComplete(request);
                } else {
                    var req = new Object();
                    req.responseText = '{"success":"false", "response":{"msg":"Unable to make request."}}';
                    options.onComplete(req);
                }
            }
        }
        request.open(this.options.method, url, this.options.asynchronous);
        this.setRequestHeaders(request);
        request.send(this.options.parameters);
    },
    setRequestHeaders: function (request) {
        var headers = {
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'text/javascript, text/html, application/xml, text/xml, */*'
        }
        headers['Content-type'] = this.options.contentType + '; charset=' + this.options.encoding;
        if (navigator.userAgent.match(/Gecko\/(\d{4})/)) headers['Connection'] = 'close';
        for (var name in headers) {
            request.setRequestHeader(name, headers[name]);
        }
    }
}
var WufooFieldLogic = Class.create({
    initialize: function () {},
    initializeFocus: function () {
        var fields = $$('.field');
        for (i = 0; i < fields.length; i++) {
            if (fields[i].type == 'radio' || fields[i].type == 'checkbox') {
                fields[i].onclick = function () {
                    fieldHighlight(this, 4);
                };
                fields[i].onfocus = function () {
                    fieldHighlight(this, 4);
                };
            } else if (fields[i].className.match('addr') || fields[i].className.match('other')) {
                fields[i].onfocus = function () {
                    fieldHighlight(this, 3);
                };
            } else {
                fields[i].onfocus = function () {
                    fieldHighlight(this, 2);
                };
            }
        }
    },
    highlight: function (el, depth) {
        if (depth == 2) {
            var fieldContainer = el.parentNode.parentNode;
        }
        if (depth == 3) {
            var fieldContainer = el.parentNode.parentNode.parentNode;
        }
        if (depth == 4) {
            var fieldContainer = el.parentNode.parentNode.parentNode.parentNode;
        }
        Element.extend(fieldContainer);
        fieldContainer.addClassName("focused");
        var focusedFields = $$('.focused');
        for (i = 0; i < focusedFields.length; i++) {
            if (focusedFields[i] != fieldContainer) {
                focusedFields[i].removeClassName('focused');
            }
        }
        if (document.getElementsByTagName('html')[0].hasClassName('embed') && $('lola')) {
            __FIELD_TOP = -5;
            while (fieldContainer) {
                __FIELD_TOP += fieldContainer.offsetTop;
                fieldContainer = fieldContainer.offsetParent;
            }
            $('lola').style.marginTop = __FIELD_TOP - $('header').offsetHeight + 'px';
        }
    },
    showRangeCounters: function () {
        var counters = $$('em.currently');
        for (i = 0; i < counters.length; i++) {
            counters[i].style.display = 'inline';
        }
    },
    validateRange: function (ColumnId, RangeType) {
        var msg = $('rangeUsedMsg' + ColumnId);
        if (msg) {
            if (RangeType == 'character') {
                var field = document.getElementById('Field' + ColumnId);
                msg.innerHTML = this.getCharacterMessage($('Field' + ColumnId));
            } else if (RangeType == 'word') {
                var field = document.getElementById('Field' + ColumnId);
                msg.innerHTML = this.getWordMessage(field);
            } else if (RangeType == 'digit') {
                msg.innerHTML = this.getDigitMessage($('Field' + ColumnId));
            }
        }
    },
    getCharacterMessage: function (field) {
        return field.value.length;
    },
    getWordMessage: function (field) {
        var val = field.value;
        val = val.replace(/\n/g, " ");
        var words = val.split(" ");
        var used = 0;
        for (i = 0; i < words.length; i++) {
            if (words[i].replace(/\s+$/, "") != "") used++;
        }
        return used;
    },
    getDigitMessage: function (field) {
        return field.value.length;
    }
});
var WufooFormLogic = Class.create({
    offset: 0,
    startTime: 0,
    endTime: 0,
    loadTime: 0,
    initialize: function () {},
    observeFormSubmit: function () {
        var activeForm = $$('form')[0];
        $(activeForm).observe('submit', this.disableSubmitButton);
        if (typeof (Event) != 'undefined') {
            if (Event.observe) Event.observe(window, 'unload', function () {});
            else Object.observeEvent(window, 'unload', function () {});
        } else {
            Object.observeEvent(window, 'unload', function () {});
        }
    },
    disableSubmitButton: function () {
        if (!$('previousPageButton')) $('saveForm').disabled = true;
    },
    ifInstructs: function () {
        var container = $('public');
        if (container) {
            if (container.offsetWidth <= 450) {
                container.addClassName('altInstruct');
            }
        }
    },
    setClick: function () {
        $('clickOrEnter').value = 'click';
    },
    beginTimer: function () {
        this.startTime = new Date().getTime() - this.loadTime;
    },
    endTimer: function () {
        this.endTime = new Date().getTime() - this.loadTime;
        stats = $('stats').value.evalJSON();
        stats.endTime += this.endTime;
        if (stats.startTime == 0) stats.startTime = this.startTime;
        $('stats').value = Object.toJSON(stats);
    },
    setLoadTime: function () {
        this.loadTime = new Date().getTime();
    },
    initAutoResize: function (additionalOffset) {
        var key = (typeof (__EMBEDKEY) != 'undefined') ? __EMBEDKEY : 'wufooForm';
        if (this.isEmbeddedForm() && key != 'false') {
            additionalOffset = this.getAdditionalOffset(additionalOffset);
            if (parent.postMessage) {
                parent.postMessage((document.body.offsetHeight + additionalOffset) + '|' + key, "*");
            } else {
                if (this.childProxyFrameExist()) {
                    this.saveHeightOnParent(document.body.offsetHeight + this.offset);
                } else {
                    this.saveHeightOnServer(key, (document.body.offsetHeight + this.offset));
                }
            }
        }
    },
    getAdditionalOffset: function (additionalOffset) {
        additionalOffset = additionalOffset || 0;
        if (navigator.userAgent.toUpperCase().indexOf('MSIE 7') != -1) {
            additionalOffset += 70;
        }
        this.offset = additionalOffset;
        return this.offset;
    },
    isEmbeddedForm: function () {
        if ($('submit_form_here')) {
            return false;
        }
        if (parent.frames.length < 1) {
            return false;
        }
        return true;
    },
    childProxyFrameExist: function () {
        var childProxyFrameExist = false;
        try {
            var childProxyFrame = parent.frames["wufooProxyFrame" + this.getFormHash()];
            if (childProxyFrame.location.href.length > 0) {
                childProxyFrameExist = true;
            }
        } catch (e) {}
        return childProxyFrameExist;
    },
    saveHeightOnParent: function (frameHeight) {
        try {
            var url = this.getURLToParent();
            parent.location.href = this.addFragment(url, '_h', frameHeight);
        } catch (e) {}
    },
    getURLToParent: function () {
        var url = parent.frames['wufooProxyFrame' + this.getFormHash()].location.href;
        url = url.substring(url.indexOf('#') + 1, url.length);
        return url;
    },
    getFormHash: function () {
        var formHashLink = document.getElementById('formHash');
        if (formHashLink) {
            return formHashLink.value;
        } else {
            var href = document.location.href;
            var hrefArray = href.split('/');
            return hrefArray[4];
        }
    },
    addFragment: function (url, fragment, frameHeight) {
        url = this.removeFragment(url, fragment);
        url += '#' + fragment + '=' + frameHeight;
        return url;
    },
    removeFragment: function (url, fragment) {
        var urlArray = url.split('#');
        return urlArray[0];
    },
    saveHeightOnServer: function (name, value) {
        this.createTempCookie(name, value);
        document.body.appendChild(this.getScriptEl(name, value));
    },
    getScriptEl: function (name, value) {
        var rules = (this.hasRules()) ? 1 : 0;
        var script = document.createElement("script");
        var src = document.location.protocol + "//wufoo.com/forms/height.js?action=set&embedKey=";
        src += name + "&height=" + value + "&rules=" + rules + "&protocol=" + document.location.protocol + "&timestamp=" + new Date().getTime().toString();
        script.setAttribute("src", src);
        script.setAttribute("type", "text/javascript");
        return script;
    },
    hasRules: function () {
        var hasRules = false;
        if (typeof (__RULES) != 'undefined') {
            if (!this.isArray(__RULES)) {
                hasRules = true;
            }
        }
        return hasRules;
    },
    isArray: function (object) {
        return object != null && typeof object == "object" && 'splice' in object && 'join' in object;
    },
    createTempCookie: function (name, height) {
        var date = new Date();
        date.setTime(date.getTime() + (60 * 1000));
        var expires = "; expires=" + date.toGMTString();
        var rules = (this.hasRules()) ? 1 : 0;
        var value = height + '|' + rules + '|' + document.location.protocol;
        document.cookie = name + "=" + value + expires + "; domain=.wufoo.com; path=/";
    },
    readTempCookie: function (name) {
        var nameEQ = name + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') c = c.substring(1, c.length);
            if (c.indexOf(nameEQ) == 0) return c.substring(nameEQ.length, c.length);
        }
        return '';
    }
});
var WufooConditions = Class.create({
    isEntryManager: false,
    isLiHidden: false,
    entry: '',
    initialize: function (isEntryManager, entry) {
        this.isEntryManager = isEntryManager;
        this.entry = entry;
    },
    match: function (rule) {
        var ret;
        for (var i = 0; i < rule.Conditions.length; i++) {
            ret = this.compare(rule.Conditions[i], rule.Setting.FieldTypes);
            if (rule.MatchType == 'any' && ret == true) {
                ret = true;
                break;
            }
            if (rule.MatchType == 'all' && ret == false) {
                ret = false;
                break;
            }
        }
        return ret;
    },
    compare: function (condition, fieldTypes) {
        var fieldType = fieldTypes[condition.FieldName];
        var fieldValue = this.cleanForComparison(this.getFieldValue(condition.FieldName, fieldType));
        var conditionValue = this.cleanForComparison(condition.Value);
        var ret = (this.isLiHidden) ? false : this[condition.Filter.replace(/ /g, '')](conditionValue, fieldValue, fieldType);
        return ret;
    },
    cleanForComparison: function (string) {
        string = string || '';
        string = string.strip().stripTags().toLowerCase();
        return string;
    },
    getFieldValue: function (columnId, fieldType) {
        var field = this.getField(fieldType, columnId);
        var value = '';
        this.isLiHidden = this.isFieldLIHidden(field);
        if (!this.isLiHidden) {
            value = this.getInputValue(this.verifyFieldType(fieldType, field), field, columnId);
        }
        return value;
    },
    getField: function (fieldType, columnId) {
        if (fieldType == 'radio' || fieldType == 'likert') {
            return this.getRadioField(fieldType, columnId);
        } else {
            return $('Field' + columnId);
        }
    },
    getRadioField: function (fieldType, columnId) {
        var counter = (fieldType == 'radio') ? 0 : 1;
        var keepSearching = true;
        var field = false;
        while (keepSearching) {
            var radioField = $('Field' + columnId + '_' + counter);
            if (radioField) {
                field = radioField;
                if (radioField.checked) {
                    keepSearching = false;
                } else {
                    counter = counter + 1;
                }
            } else {
                keepSearching = false;
            }
        }
        if (field && fieldType == 'radio') {
            if (field.value == 'Other') {
                var otherField = $('Field' + columnId + '_other');
                if (otherField) field = otherField;
            }
        }
        return field;
    },
    verifyFieldType: function (fieldType, field) {
        if (fieldType == 'radio' && field) {
            if (field.id.endsWith('_other')) {
                fieldType = 'text';
            }
        }
        return fieldType;
    },
    isFieldLIHidden: function (field) {
        if (field) {
            var fieldLI = this.getFieldLI(field);
            if ((fieldLI.hasClassName('hide') && !this.isEntryManager) || fieldLI.hasClassName('rule_hide')) {
                return true;
            }
        }
        return false;
    },
    getFieldLI: function (field) {
        var count = 0;
        var parent = field.parentNode;
        while (count < 100) {
            if (parent.tagName.toLowerCase() == 'li' && parent.id.startsWith('fo')) {
                return $(parent);
            }
            count = count + 1;
            parent = parent.parentNode;
        }
        return field;
    },
    getInputValue: function (fieldType, field, columnId) {
        if (field) {
            return this.getInputValueFromCurrentPage(fieldType, field, columnId);
        } else {
            return this.getInputValueFromEntry(fieldType, columnId);
        }
    },
    getInputValueFromCurrentPage: function (fieldType, field, columnId) {
        var value = '';
        switch (fieldType) {
        case 'time':
            value = this.getTimeInputValue(columnId);
            break;
        case 'eurodate':
            value = this.getEuroDateInputValue(columnId);
            break;
        case 'date':
            value = this.getDateInputValue(columnId);
            break;
        case 'phone':
            value = this.getPhoneInputValue(columnId);
            break;
        case 'money':
            value = this.getMoneyInputValue(columnId);
            break;
        case 'checkbox':
            value = this.getCheckboxInputValue(field);
            break;
        case 'radio':
            value = this.getRadioInputValue(field);
            break;
        default:
            value = this.getSimpleInputValue(field);
            break;
        }
        return value;
    },
    getTimeInputValue: function (columnId) {
        var hour = $('Field' + columnId).value;
        var min = $('Field' + columnId + '-1').value;
        var sec = $('Field' + columnId + '-2').value;
        var amPm = $('Field' + columnId + '-3').value;
        if (amPm == 'PM' && hour < 12) {
            hour = hour * 1;
            hour = hour + 12;
        }
        return this.formatNum(hour) + ':' + this.formatNum(min) + ':' + this.formatNum(sec);
    },
    getEuroDateInputValue: function (columnId) {
        var year = $('Field' + columnId).value;
        var month = $('Field' + columnId + '-2').value;
        var day = $('Field' + columnId + '-1').value;
        return year + '-' + this.formatNum(month) + '-' + this.formatNum(day);
    },
    getDateInputValue: function (columnId) {
        var year = $('Field' + columnId).value;
        var month = $('Field' + columnId + '-1').value;
        var day = $('Field' + columnId + '-2').value;
        return year + '-' + this.formatNum(month) + '-' + this.formatNum(day);
    },
    formatNum: function (num) {
        num = new String(num) || '';
        return (num.length == 1) ? '0' + num : num;
    },
    getPhoneInputValue: function (columnId) {
        var phone = $('Field' + columnId).value + $('Field' + columnId + '-1').value + $('Field' + columnId + '-2').value;
        return phone;
    },
    getMoneyInputValue: function (columnId) {
        var integer = $('Field' + columnId).value;
        var digit = $('Field' + columnId + '-1').value;
        if (digit > 0) integer = integer + '.' + digit;
        return integer;
    },
    getCheckboxInputValue: function (field) {
        if (field.checked) return field.value;
        else return '';
    },
    getRadioInputValue: function (field) {
        if (field.checked) return field.value;
        else return '';
    },
    getSimpleInputValue: function (field) {
        return field.value;
    },
    getInputValueFromEntry: function (fieldType, columnId) {
        var value = new String(this.entry['Field' + columnId]);
        if (fieldType == 'date' || fieldType == 'eurodate') {
            value = value.substring(0, 4) + '-' + value.substring(4, 6) + '-' + value.substring(6, 8);
        }
        return value;
    },
    contains: function (needle, haystack) {
        if (needle == '' && haystack == '') return true;
        if (needle == '' && haystack != '') return false;
        if (haystack.indexOf(needle) == -1) return false;
        else return true;
    },
    doesnotcontain: function (needle, haystack) {
        if (needle == '' && haystack == '') return false;
        if (needle == '' && haystack != '') return true;
        if (haystack.indexOf(needle) == -1) return true;
        else return false;
    },
    is: function (needle, haystack) {
        return this.isequalto(needle, haystack);
    },
    isequalto: function (needle, haystack) {
        if (needle == haystack) return true;
        else return false;
    },
    isnot: function (needle, haystack) {
        return this.isnotequalto(needle, haystack);
    },
    isnotequalto: function (needle, haystack) {
        if (needle != haystack) return true;
        else return false;
    },
    beginswith: function (needle, haystack) {
        if (haystack.indexOf(needle) === 0) return true;
        else return false;
    },
    endswith: function (needle, haystack) {
        var d = haystack.length - needle.length;
        if (d >= 0 && haystack.lastIndexOf(needle) === d) return true;
        else return false;
    },
    isgreaterthan: function (conditionValue, fieldValue) {
        if (!this.isEmpty(fieldValue)) {
            conditionValue = new Number(conditionValue);
            fieldValue = new Number(fieldValue);
            if (fieldValue > conditionValue) return true;
            else return false;
        } else {
            return false;
        }
    },
    islessthan: function (conditionValue, fieldValue) {
        if (!this.isEmpty(fieldValue)) {
            conditionValue = new Number(conditionValue);
            fieldValue = new Number(fieldValue);
            if (fieldValue < conditionValue) return true;
            else return false;
        } else {
            return false;
        }
    },
    isat: function (conditionDate, dateValue, type) {
        return (conditionDate == dateValue);
    },
    ison: function (conditionDate, dateValue, type) {
        return (conditionDate == dateValue);
    },
    isbefore: function (conditionDate, dateValue, type) {
        if (type == 'time') {
            return this.compareTimes(conditionDate, dateValue, 'isbefore');
        } else {
            return this.compareDates(conditionDate, dateValue, 'isbefore');
        }
    },
    isafter: function (conditionDate, dateValue, type) {
        if (type == 'time') {
            return this.compareTimes(conditionDate, dateValue, 'isafter');
        } else {
            return this.compareDates(conditionDate, dateValue, 'isafter');
        }
    },
    compareDates: function (conditionDate, dateValue, compareType) {
        var condArray = this.cleanSplit('-', conditionDate, 3);
        var dateArray = this.cleanSplit('-', dateValue, 3);
        var condDate = new Date(condArray[0], condArray[1], condArray[2], 1, 1, 1, 1);
        var date = new Date(dateArray[0], dateArray[1], dateArray[2], 1, 1, 1, 1);
        if (dateArray[0].length < 4 || dateArray[1].length < 2 || dateArray[0].length < 2) {
            return false;
        }
        if (compareType == 'isbefore') {
            return (date < condDate);
        } else {
            return (date > condDate);
        }
    },
    compareTimes: function (conditionTime, timeValue, compareType) {
        var condArray = this.cleanSplit(':', conditionTime, 3);
        var timeArray = this.cleanSplit(':', timeValue, 3);
        var condTime = new Date(2000, 1, 1, condArray[0], condArray[1], condArray[2], 1);
        var time = new Date(2000, 1, 1, timeArray[0], timeArray[1], timeArray[2], 1);
        if (timeArray[0].length < 2 || timeArray[1].length < 2 || timeArray[2].length < 2) {
            return false;
        }
        if (compareType == 'isbefore') {
            return (time < condTime);
        } else {
            return (time > condTime);
        }
    },
    cleanSplit: function (delimiter, text, expectedLength) {
        var textArray = text.split(delimiter);
        for (var i = 0; i < expectedLength; i++) {
            textArray[i] = textArray[i] || '';
        }
        return textArray;
    },
    isEmpty: function (value) {
        value = value || '';
        value = value.strip();
        if (value == '') {
            return true;
        } else {
            return false;
        }
    }
});
var WufooRuleLogic = Class.create({
    formId: '',
    valueHash: {},
    hiddenHash: {},
    isEntryManager: false,
    Rules: '',
    PublicForm: '',
    ConditionService: '',
    Entry: new Object(),
    RulesByConditionFieldName: new Object(),
    processAfterShowArray: new Object(),
    initialize: function (publicForm) {
        this.PublicForm = publicForm;
        this.initializeVariables(this.PublicForm.isEntryManager);
        this.attachFakeOnchangeToRadioButtons();
        this.attachOnChangeToSelectFields();
    },
    initializeVariables: function (isEntryManager) {
        if (typeof (__RULES) != 'undefined') {
            this.Rules = __RULES;
            this.RulesByTargetField = this.organizeRulesByTargetField();
        }
        if (typeof (__ENTRY) != 'undefined') {
            this.Entry = __ENTRY;
        }
        if (typeof (isEntryManager) != 'undefined') this.isEntryManager = isEntryManager;
        this.ConditionService = new WufooConditions(this.isEntryManager, this.Entry);
    },
    attachFakeOnchangeToRadioButtons: function () {
        if (this.isEntryManager) {
            var liInputs = $$('li input.field');
        } else {
            var liInputs = Selector.findElementsByTagAndClass(null, 'input', null);
        }
        for (var i = 0; i < liInputs.length; i++) {
            var input = liInputs[i];
            if (input.type == 'checkbox' || input.type == 'radio') {
                input.onclick = function () {
                    this.blur();
                    this.focus();
                };
            }
        }
    },
    attachOnChangeToSelectFields: function () {
        if (this.attachOnChangeToSelect()) {
            var liInputs = (this.isEntryManager) ? $$('li select.field') : Selector.findElementsByTagAndClass(null, 'select', null);
            for (var i = 0; i < liInputs.length; i++) {
                liInputs[i].onchange = function () {
                    handleInput(this);
                };
            }
        }
    },
    attachOnChangeToSelect: function () {
        if (navigator.userAgent.toLowerCase().indexOf('chrome') != -1) {
            return true;
        } else if (Prototype.Browser.MobileSafari) {
            return true;
        } else if (navigator.appVersion.indexOf("Win") != -1 && Prototype.Browser.WebKit) {
            return true;
        } else if (navigator.appVersion.indexOf("Mac") != -1 && Prototype.Browser.Opera) {
            return true;
        } else {
            return false;
        }
    },
    organizeRulesByTargetField: function () {
        for (var fieldId in this.Rules) {
            var rules = this.Rules[fieldId];
            for (var i = 0; i < rules.length; i++) {
                var rule = rules[i];
                if (typeof (rule) != 'undefined') {
                    if (this.formId == '') this.formId = rule.FormId;
                    for (var j = 0; j < rule.Conditions.length; j++) {
                        var c = rule.Conditions[j];
                        this.addToRulesByConditionFieldName(rule.Setting.FieldName, c.FieldName);
                    }
                }
            }
        }
    },
    addToRulesByConditionFieldName: function (key, value) {
        var columnId = 'Field' + value;
        if (Object.isArray(this.RulesByConditionFieldName[key])) {
            if (this.RulesByConditionFieldName[key].indexOf(columnId) == -1) {
                this.RulesByConditionFieldName[key].push(columnId);
            }
        } else {
            this.RulesByConditionFieldName[key] = new Array(columnId);
        }
    },
    process: function (el, count) {
        var rules = this.getRules(el);
        if (typeof (rules) != 'undefined') {
            rules.each(function (rule) {
                if (this.ConditionService.match(rule)) {
                    this.toggleDisplay(rule, false, count)
                } else {
                    this.toggleDisplay(rule, true, count);
                }
            }.bind(this));
        }
    },
    getRules: function (el) {
        var idArray = el.id.split('_');
        idArray = idArray[0].split('-');
        return this.Rules[idArray[0]];
    },
    toggleDisplay: function (rule, inverse, count) {
        var liEl = this.getLiEl(rule.FormId, rule.Setting.FieldName);
        var className = (this.isEntryManager) ? 'rule_hide' : 'hide';
        if (liEl) {
            var originalClassName = liEl.className.strip().replace(/\n/g, '');
            if ((rule.Type == 'Show' && !inverse) || (rule.Type == 'Hide' && inverse)) {
                liEl.removeClassName(className);
            }
            if ((rule.Type == 'Hide' && !inverse) || (rule.Type == 'Show' && inverse)) {
                liEl.removeClassName('error');
                liEl.removeClassName('hide');
                liEl.removeClassName('rule_hide');
                liEl.addClassName(className);
            }
            var newClassName = liEl.className.strip().replace(/\n/g, '');
            if (originalClassName != newClassName) {
                this.processElAfterShow(liEl, rule.Setting.FieldName, count);
            }
            this.PublicForm.handleTabs();
        }
    },
    processElAfterShow: function (liEl, fieldName, count) {
        this.processThreeStandardInputs(liEl, count);
        var conditionIds = this.RulesByConditionFieldName[fieldName];
        for (var i = 0; i < conditionIds.length; i++) {
            var conditionLiEl = this.getLiEl(this.formId, conditionIds[i]);
            if (conditionLiEl) this.processThreeStandardInputs(conditionLiEl, count);
        }
    },
    getLiEl: function (formId, fieldName) {
        fieldName = fieldName.replace('Field', '');
        var liEl = $('fo' + formId + 'li' + fieldName);
        liEl = (liEl) ? liEl : $('foli' + fieldName);
        return liEl;
    },
    processThreeStandardInputs: function (liEl, count) {
        this.findInputsAndCallHandleInput('input', liEl, count);
        this.findInputsAndCallHandleInput('select', liEl, count);
        this.findInputsAndCallHandleInput('textarea', liEl, count);
    },
    procceedWithComparisonOld: function (liEl) {
        var proceed = false;
        if (liEl) {
            if (!liEl.hasClassName('hide') && !liEl.hasClassName('rule_hide')) {
                proceed = true;
            }
        }
        return proceed;
    },
    findInputsAndCallHandleInput: function (tag, liEl, count) {
        var liInputs;
        if (this.isEntryManager) {
            liInputs = $$('#main #' + liEl.id + ' ' + tag + '.field');
        } else {
            liInputs = Selector.findElementsByTagAndClass(null, tag, liEl);
        }
        liInputs = this.findInputsToHandle(liInputs);
        this.handleInputOnAllElements(liEl, liInputs, count);
    },
    findInputsToHandle: function (liInputs) {
        var hasRadio = false;
        var radioInputs = new Array();
        for (var i = 0; i < liInputs.length; i++) {
            var input = liInputs[i];
            if (input.type == 'radio') {
                hasRadio = true;
                if (input.checked) radioInputs.push(input);
            }
        }
        if (hasRadio) return radioInputs;
        else return liInputs;
    },
    handleInputOnAllElements: function (liEl, inputs, count) {
        for (var i = 0; i < inputs.length; i++) {
            var input = inputs[i];
            if (this.procceedWithComparison(liEl, input)) {
                this.addToProcessAfterShowArray(input, count);
                if (this.processAfterShowArray[input.id] < 3) {
                    if (input.type == 'radio') {
                        if (input.checked) {
                            this.process(inputs[i], 1);
                        }
                    } else {
                        this.process(inputs[i], 1);
                    }
                }
            }
        }
    },
    procceedWithComparison: function (liEl, input) {
        var proceedWithComparison = false;
        var hiddenValue = ((liEl.hasClassName('hide') && !this.isEntryManager) || liEl.hasClassName('rule_hide')) ? 'hide' : '';
        if (typeof (this.valueHash[input.id]) == 'undefined' || typeof (this.hiddenHash[liEl.id]) == 'undefined') {
            proceedWithComparison = true;
        } else if (this.valueHash[input.id] != input.value || this.hiddenHash[liEl.id] != hiddenValue) {
            proceedWithComparison = true;
        }
        this.valueHash[input.id] = input.value;
        this.hiddenHash[liEl.id] = hiddenValue;
        return proceedWithComparison;
    },
    addToProcessAfterShowArray: function (input, count) {
        if (typeof (count) == 'undefined' || count < 1) {
            this.processAfterShowArray[input.id] = 0;
        } else {
            var elCount = this.processAfterShowArray[input.id];
            elCount = isNaN(elCount) ? 0 : elCount;
            this.processAfterShowArray[input.id] = elCount + 1;
        }
    }
});
var RunningTotal = Class.create({
    entry: '',
    decimals: 2,
    basePrice: 0,
    currency: '',
    totalText: '',
    basePriceText: '',
    publicForm: '',
    merchantFields: [],
    hiddenClassNames: ['rule_hide', 'hide'],
    tableTmpl: '<table id="run" border="0" cellspacing="0" cellpadding="0">' + '<tfoot>' + '#{rows}' + '</tfoot>' + '<tbody>' + '<tr><td colspan="2"><b>#{totalText}</b><span>#{total}</span></td></tr>' + '</tbody>' + '</table>',
    initialize: function (publicForm) {
        this.publicForm = publicForm;
        if (this.showRunningTotal()) {
            this.makeGlobalVariablesSafe();
            this.entry = __ENTRY;
            this.currency = __PRICES.Currency;
            this.decimals = __PRICES.Decimals;
            this.totalText = __PRICES.TotalText;
            this.basePriceText = __PRICES.BasePriceText;
            this.basePrice = this.toNumber(__PRICES.BasePrice);
            this.organizeMerchantFields(__PRICES.MerchantFields);
            this.updateTotal();
            this.calculateTop();
            this.runLolaRun();
            if (!document.getElementsByTagName('html')[0].hasClassName('embed')) {
                Object.observeEvent(window, 'scroll', this.runLolaRun);
            }
        }
    },
    showRunningTotal: function () {
        var canShowRunningTotal = false;
        if (!this.publicForm.isEntryManager) {
            if ($('lola')) {
                canShowRunningTotal = true;
            }
        }
        return canShowRunningTotal;
    },
    makeGlobalVariablesSafe: function () {
        __ENTRY = (typeof (__ENTRY) == 'undefined') ? {} : __ENTRY;
        if (typeof (__PRICES) == 'undefined' || !__PRICES) {
            __PRICES = {
                BasePrice: 0,
                Currency: '&#36;',
                MerchantFields: []
            };
        }
    },
    calculateTop: function () {
        var el = $('lola');
        __PRICE_TOP = -14;
        while (el) {
            __PRICE_TOP += el.offsetTop;
            el = el.offsetParent;
        }
    },
    runLolaRun: function () {
        var scrollTop = window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop;
        if (scrollTop >= __PRICE_TOP) {
            $('lola').style.marginTop = scrollTop - __PRICE_TOP + 7 + 'px';
        } else {
            $('lola').style.marginTop = 7 + 'px';
        }
    },
    organizeMerchantFields: function (rawMerchantFields) {
        this.merchantFields = new Array();
        for (var i = 0; i < rawMerchantFields.length; i++) {
            var field = rawMerchantFields[i];
            if (field.Typeof == 'checkbox') {
                for (var key in field.SubFields) {
                    if (typeof (field.SubFields[key]) == 'object') {
                        this.addToMerchantFields(field, field.SubFields[key], 0);
                    }
                }
            } else if (field.Typeof == 'radio' || field.Typeof == 'select') {
                var index = 0;
                for (var key in field.Choices) {
                    if (typeof (field.Choices[key]) == 'object') {
                        this.addToMerchantFields(field, field.Choices[key], index);
                        index += 1;
                    }
                }
            } else if (field.Typeof == 'money') {
                this.addToMerchantFields(field, null, 0);
            }
        }
    },
    addToMerchantFields: function (field, subObj, i) {
        var merchantField = new Object();
        var obj = (field.Typeof == 'money') ? field : subObj;
        merchantField.ColumnId = 'Field' + obj.ColumnId;
        merchantField.Price = obj.Price;
        merchantField.Choice = obj.Choice;
        merchantField.Typeof = field.Typeof;
        merchantField.Index = i;
        if (field.Typeof == 'checkbox') {
            merchantField.Header = obj.ChoicesText;
        } else if (field.Typeof == 'money') {
            merchantField.Header = obj.Title;
        } else {
            merchantField.Header = obj.Choice;
        }
        this.merchantFields.push(merchantField);
    },
    updateTotal: function () {
        if ($('lola')) {
            var fieldToPrices = this.getFieldToPrices();
            var tableHTML = this.buildRunningTotalTable(fieldToPrices);
            $('lola').innerHTML = tableHTML;
        }
    },
    buildRunningTotalTable: function (fieldToPrices) {
        var html = '';
        var total = this.basePrice;
        if (this.basePrice > 0) {
            html += '<tr><th>' + this.basePriceText + '</th>';
            html += '<td>' + this.formatNumber(this.basePrice) + '</td></tr>';
        }
        for (var i = 0; i < fieldToPrices.length; i++) {
            var fieldToPrice = fieldToPrices[i];
            total = total + fieldToPrice.fieldValue;
            var className = (fieldToPrice.fieldValue < 0) ? 'negAmount' : '';
            html += '<tr class="' + className + '"><th>' + fieldToPrice.field.Header + '</th>';
            html += '<td>' + this.formatNumber(fieldToPrice.fieldValue) + '</td></tr>';
        }
        var template = new Template(this.tableTmpl);
        tplValues = {
            'totalText': this.totalText,
            'total': this.formatNumber(total),
            'rows': html
        };
        return template.evaluate(tplValues);
    },
    formatNumber: function (num) {
        var isNegative = (num < 0) ? true : false;
        num = Math.abs(num);
        num = num.toFixed(this.decimals);
        num = this.addCommas(num);
        num = this.currency + num;
        num = (isNegative) ? '-' + num : num;
        return num;
    },
    addCommas: function (nStr) {
        nStr += '';
        x = nStr.split('.');
        x1 = x[0];
        x2 = x.length > 1 ? '.' + x[1] : '';
        var rgx = /(\d+)(\d{3})/;
        while (rgx.test(x1)) {
            x1 = x1.replace(rgx, '$1' + ',' + '$2');
        }
        return x1 + x2;
    },
    getFieldToPrices: function () {
        var fieldToPrices = new Array();
        for (var i = 0; i < this.merchantFields.length; i++) {
            fieldValue = this.getFieldValue(this.merchantFields[i]);
            fieldValue = (this.merchantFields[i].Typeof == 'money' && fieldValue < 0) ? 0.00 : fieldValue;
            if (fieldValue > 0 || fieldValue < 0) {
                fieldToPrice = {
                    'fieldValue': fieldValue,
                    'field': this.merchantFields[i]
                };
                fieldToPrices.push(fieldToPrice);
            }
        }
        return fieldToPrices;
    },
    getFieldValue: function (field) {
        var value = 0;
        var el = this.getElement(field);
        if (el) {
            if (!this.isElementHidden(el)) {
                value = this.getElementValue(field, el);
            }
        } else {
            if (field.Typeof == 'money') {
                value = this.entry[field.ColumnId];
            } else if (field.Typeof == 'checkbox') {
                if (typeof (this.entry[field.ColumnId]) != 'undefined' && this.entry[field.ColumnId] != '') {
                    value = field.Price;
                }
            } else if (field.Typeof == 'radio' || field.Typeof == 'select') {
                if (field.Choice == this.entry[field.ColumnId]) {
                    value = field.Price;
                }
            }
        }
        return this.toNumber(value);
    },
    getElement: function (field) {
        if (field.Typeof == 'radio') {
            return $(field.ColumnId + '_' + field.Index);
        } else {
            return $(field.ColumnId);
        }
    },
    isElementHidden: function (el) {
        var isElementHidden = false;
        var li = this.publicForm.getFieldLI(el);
        if (this.hasHiddenClassName(li)) {
            isElementHidden = true;
        }
        return isElementHidden;
    },
    hasHiddenClassName: function (li) {
        var hasClassName = false;
        for (var i = 0; i < this.hiddenClassNames.length; i++) {
            if (li.hasClassName(this.hiddenClassNames[i])) {
                hasClassName = true;
                break;
            }
        }
        return hasClassName;
    },
    getElementValue: function (field, el) {
        if (field.Typeof == 'checkbox' || field.Typeof == 'radio') {
            return (el.checked) ? field.Price : 0;
        } else if (field.Typeof == 'select') {
            return (field.Choice == el.value) ? field.Price : 0;
        } else if (field.Typeof == 'money') {
            var integer = $(field.ColumnId).value;
            var decimal = $(field.ColumnId + '-1').value;
            return new String(integer + '.' + decimal);
        }
    },
    toNumber: function (numAsString) {
        if (typeof (numAsString) == 'undefined') {
            numAsString = '0';
        }
        var num = parseFloat(numAsString);
        num = (isNaN(num)) ? 0.00 : num;
        return num;
    }
});
var PublicForm = Class.create({
    formLogic: new WufooFormLogic(),
    fieldLogic: new WufooFieldLogic(),
    runningTotal: '',
    ruleLogic: '',
    formHeight: '',
    timerActive: false,
    genericInputs: {},
    sortedTabindexes: [],
    isEntryManager: false,
    initialize: function (runInit, isEntryManager) {
        this.isEntryManager = isEntryManager;
        if (runInit) this.runInit();
        this.ruleLogic = new WufooRuleLogic(this);
        this.runningTotal = new RunningTotal(this);
    },
    runInit: function () {
        var redirectingToPaymentPage = this.continueToPaypal();
        this.continueToMechanicalTurk();
        this.formLogic.setLoadTime();
        this.formLogic.observeFormSubmit();
        this.fieldLogic.initializeFocus();
        this.fieldLogic.showRangeCounters();
        if (!redirectingToPaymentPage) this.formLogic.initAutoResize(0);
        this.setFormHeight();
        this.handleTabs();
    },
    handleTabs: function () {
        if (Prototype.Browser.IE || Prototype.Browser.Opera) return;
        var inputs;
        this.genericInputs = {};
        this.sortedTabindexes = [];
        if (this.isEntryManager) {
            inputs = $$('#main #entry_form input');
            inputs = inputs.concat($$('#main #entry_form textarea'));
            inputs = inputs.concat($$('#main #entry_form select'));
        } else {
            inputs = $$('input');
            inputs = inputs.concat($$('textarea'));
            inputs = inputs.concat($$('select'));
        }
        var validInputs = new Array();
        for (var i = 0; i < inputs.length; i++) {
            var li = this.getFieldLI(inputs[i]);
            if (li.hasClassName('hideAddr2')) {
                if (!inputs[i].parentNode.hasClassName('addr2')) {
                    validInputs.push(inputs[i]);
                }
            } else if (li.hasClassName('hideAMPM')) {
                if (!inputs[i].parentNode.hasClassName('ampm')) {
                    validInputs.push(inputs[i]);
                }
            } else if (li.hasClassName('hideSeconds')) {
                if (!inputs[i].parentNode.hasClassName('seconds')) {
                    validInputs.push(inputs[i]);
                }
            } else if (li.hasClassName('hideCents')) {
                if (!inputs[i].parentNode.hasClassName('cents')) {
                    validInputs.push(inputs[i]);
                }
            } else if (li.hasClassName('rule_hide') || li.hasClassName('hide') || li.hasClassName('cloak')) {} else if (inputs[i].id == 'comment') {} else if (inputs[i].type != 'hidden') {
                validInputs.push(inputs[i]);
            }
        }
        inputs = validInputs;
        var noTabIndexes = new Array();
        var highestTabIndex = 1;
        for (var i = 0; i < inputs.length; i++) {
            var tabIndex = inputs[i].getAttribute('tabindex');
            if (tabIndex > 0 && this.sortedTabindexes.indexOf(tabIndex) == -1) {
                if (Prototype.Browser.Gecko && inputs[i].type == 'file') continue;
                if (tabIndex > highestTabIndex) {
                    highestTabIndex = tabIndex;
                }
                inputs[i].observe('keydown', tabToInput);
                this.genericInputs[inputs[i].getAttribute('tabindex')] = inputs[i];
                this.sortedTabindexes.push(inputs[i].getAttribute('tabindex'));
            } else {
                noTabIndexes.push(inputs[i]);
            }
        }
        this.sortedTabindexes.sort(function (a, b) {
            return a - b;
        });
        for (var i = 0; i < noTabIndexes.length; i++) {
            highestTabIndex = highestTabIndex + 1;
            noTabIndexes[i].observe('keydown', tabToInput);
            noTabIndexes[i].setAttribute('tabindex', highestTabIndex);
            this.genericInputs[highestTabIndex] = noTabIndexes[i];
            this.sortedTabindexes.push(highestTabIndex);
        }
    },
    getFieldLI: function (field) {
        var count = 0;
        var parent = field.parentNode;
        while (count < 100) {
            if (parent.tagName.toLowerCase() == 'li' && parent.id.startsWith('fo')) {
                return $(parent);
            }
            count = count + 1;
            parent = parent.parentNode;
            if (parent.tagName.toLowerCase() == 'body') count = count + 100;
        }
        return field;
    },
    tabToInput: function (event) {
        if (event.keyCode == 9 && this.sortedTabindexes.length > 0) {
            var nextField;
            var currTabIndex = new Number(event.currentTarget.getAttribute('tabindex'));
            var firstInputElement = this.genericInputs[this.sortedTabindexes[0]];
            var lastInputElement = this.genericInputs[this.sortedTabindexes[this.sortedTabindexes.length - 1]];
            if (!event.shiftKey && currTabIndex == lastInputElement.getAttribute('tabindex')) {
                nextField = firstInputElement;
            } else if (event.shiftKey && currTabIndex == firstInputElement.getAttribute('tabindex')) {
                nextField = lastInputElement;
            } else {
                for (var i = 0; i < this.sortedTabindexes.length; i++) {
                    if (this.sortedTabindexes[i] == currTabIndex) {
                        var nextTabIndex = (event.shiftKey) ? this.sortedTabindexes[i - 1] : this.sortedTabindexes[i + 1];
                        nextField = this.genericInputs[nextTabIndex];
                        break;
                    }
                }
            }
            if (nextField) {
                nextField.focus();
                if (event && event.preventDefault) event.preventDefault();
                else return false;
            }
        }
    },
    setFormHeight: function () {
        this.formHeight = document.body.offsetHeight + this.formLogic.offset;
    },
    continueToPaypal: function () {
        var redirectingToPaymentPage = false;
        if ($('merchant')) {
            redirectingToPaymentPage = true;
            if ($('merchantButton')) {
                $('merchantMessage').innerHTML = $('merchantMessageText').innerHTML;
                $('merchantButton').style.display = 'none';
            }
            $('merchant').submit();
        }
        return redirectingToPaymentPage;
    },
    continueToMechanicalTurk: function () {
        if ($('mechanicalTurk')) {
            $('merchantMessage').innerHTML = 'Your submission is being processed. You will be redirected shortly.';
            $('merchantButton').style.display = 'none';
            $('mechanicalTurk').submit();
        }
    },
    deleteFile: function (fieldId, file_name, container, removal, removeFile, formId) {
        Ajax.Request('/forms/File.Change.php', {
            parameters: "entryId=" + $('EntryId').value + "&fieldId=" + fieldId + "&fileName=" + file_name + "&formId=" + formId,
            onComplete: function (r) {
                var ret = r.responseText.evalJSON();
                finishDeleteFile(ret, removeFile, removal, container);
            }
        });
    },
    failedDeleteFile: function (ret) {
        alert('We were unable to change your file.');
    },
    successfulDeleteFile: function (removeFile, removal, container) {
        $(removeFile).parentNode.removeChild($(removeFile));
        $(removal).style.display = 'none';
        $(container).style.display = 'block';
    },
    handleInput: function (el, count) {
        if (!this.timerActive) {
            this.formLogic.beginTimer();
            this.timerActive = true;
        }
        this.ruleLogic.process(el, count);
        this.runningTotal.updateTotal(el);
        this.adjustFormHeight();
    },
    doSubmitEvents: function () {
        this.formLogic.endTimer();
        this.formLogic.setClick();
    },
    adjustFormHeight: function () {
        var currentHeight = document.body.offsetHeight + this.formLogic.offset;
        if (this.formHeight != currentHeight) {
            this.formLogic.initAutoResize(this.formLogic.offset);
            this.setFormHeight();
        }
    },
    selectDateOnForm: function (cal) {
        selectDate(cal);
        var p = cal.params;
        var year = p.inputField.id;
        $(year).onchange();
    },
    selectEuroDateOnForm: function (cal) {
        selectEuroDate(cal);
        var p = cal.params;
        var year = p.inputField.id;
        $(year).onchange();
    }
});
var __PF;
Object.observeEvent(window, 'load', init);

function init() {
    __PF = new PublicForm(true);
}

function tabToInput(event) {
    __PF.tabToInput(event);
}

function fieldHighlight(el, depth) {
    __PF.fieldLogic.highlight(el, depth);
}

function validateRange(ColumnId, RangeType) {
    __PF.fieldLogic.validateRange(ColumnId, RangeType);
}

function deleteFile(fieldId, file_name, container, removal, removeFile, formId) {
    __PF.deleteFile(fieldId, file_name, container, removal, removeFile, formId);
}

function finishDeleteFile(ret, removeFile, removal, container) {
    if (ret.success == 'false') __PF.failedDeleteFile(ret);
    else __PF.successfulDeleteFile(removeFile, removal, container);
}

function handleInput(el, count) {
    __PF.handleInput($(el), count);
}

function selectDateOnForm(cal) {
    __PF.selectDateOnForm(cal);
}

function selectEuroDateOnForm(cal) {
    __PF.selectEuroDateOnForm(cal);
}

function doSubmitEvents() {
    __PF.doSubmitEvents();
}
