autosize(document.querySelectorAll("textarea"));

$("select[multiple]").selectize();

$("input[cleave=dkp]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralThousandsGroupStyle: "thousand",
		numeralDecimalScale: 2,
		numeralDecimalMark: ".",
		delimiter: "'",
		noImmediatePrefix: true
	})
});

$("input[cleave=item]").toArray().forEach(function (field) {
	new Cleave(field, {
		numeral: true,
		numeralDecimalScale: 0,
		numeralThousandsGroupStyle: "none"
	})
});

$("tbody.with-details .details-toggle").parent("tr").click(function () {
	$(this).toggleClass("details-shown");
});

$("tbody.with-details a").click(function (e) {
	e.stopPropagation();
});
