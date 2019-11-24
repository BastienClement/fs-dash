$.debounce = function (delay, func) {
	var timer;
	return function() {
		clearTimeout(timer);
		var args = arguments;
		var self = this;
		timer = setTimeout(function() {
			func.apply(self, args);
		}, delay);
	}
}

autosize(document.querySelectorAll("textarea"));

$("select[multiple], select.selectize").selectize();

$("input[cleave=dkp]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralThousandsGroupStyle: "thousand",
		numeralDecimalScale: 2,
		numeralIntegerScale: 5,
		numeralDecimalMark: ".",
		delimiter: "'",
		noImmediatePrefix: true
	})
});

$("input[cleave=dkp-positive]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralThousandsGroupStyle: "thousand",
		numeralPositiveOnly: true,
		numeralDecimalScale: 2,
		numeralIntegerScale: 5,
		numeralDecimalMark: ".",
		delimiter: "'",
		noImmediatePrefix: true
	})
});

$("input[cleave=item]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralDecimalScale: 0,
		numeralPositiveOnly: true,
		numeralThousandsGroupStyle: "none"
	})
});

$("input[cleave=count]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralDecimalScale: 0,
		numeralIntegerScale: 3,
		numeralPositiveOnly: true,
		numeralThousandsGroupStyle: "none"
	})
});

$("tbody.with-details .details-toggle").parent("tr").click(function () {
	$(this).toggleClass("details-shown");
});

$("tbody.with-details a").click(function (e) {
	e.stopPropagation();
});
