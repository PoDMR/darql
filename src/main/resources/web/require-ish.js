module = { exports: window };
exports = module.exports;

function require(name) {
	var key = name.replace(/(\-\w)/g, function(m) { return m[1].toUpperCase(); });
	if (window[key]) {
		return window[key];
	} else {
		key = key.replace(/^./, function(s) { return s.toUpperCase()} );
		return window[key];
	}
}
