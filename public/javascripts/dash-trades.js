$("#item-id").on("keyup", $.debounce(500, function () {
	var id = parseInt(this.value);
	if (!id || id < 1) return;

	var $output = $("#item-output");
	var $content = $("main", $output);
	var $button = $("#item-create-button");

	$button.attr("disabled", "disabled");
	$output.hide();

	$.get("/trades/catalog/fetch-item", { id: id })
		.then(function (res) {
			if (res.item) {
				$output.show().removeClass("error");
				$content.html("<a href='https://classic.wowhead.com/item=" + res.item.id + "'></a>");

				$("a", $content)
					.addClass("whlink icontinyl q" + res.item.quality)
					.css({
						"padding-left": "18px !important",
						"background": "url('https://wow.zamimg.com/images/wow/icons/tiny/" + res.item.icon + ".gif') left center no-repeat"
					})
					.text(res.item.name);

				$button.attr("disabled", null);
				$button.off("click").on("click", function () {
					document.location.href = '/trades/catalog/sku/' + id;
					return false;
				});
			} else {
				$output.show().addClass("error");
				$content.text(res.err);
			}
		})
		.fail(function (err) {
			$output.show().addClass("error");
			$content.text("Une erreur est survenue.")
		})
}));

$("form[data-kind]").each(function (_, form) {
	var $quantity = $("input[type=text]", form);
	var $button = $("button", form)
	var $error = $(".error", form.parentElement);

	var kind = form.dataset.kind;
	var limit = window["trades_" + kind + "_limit"];
	var price = window["trades_" + kind + "_price"];

	function formatAmount(amount) {
		var int = Math.floor(amount / 100);
		var fractional = amount % 100;
		return "" + int + "." + (fractional < 10 ? "0" : "") + fractional;
	}

	function update() {
		var quantity = parseInt($quantity.val());
		$button.attr('disabled', 'disabled');
		$error.hide();

		if (quantity) {
			var total = quantity * price;
			if (total > limit) {
				$error.html("Le montant total (" + formatAmount(total) + ") est supérieur à la limite autorisée (" + formatAmount(limit) + ").").show();
				return;
			}

			var balance = window["trades_account_balance"];
			if (kind === "bid" && total > balance) {
				$error.html("Le montant total (" + formatAmount(total) + ") est supérieur à votre solde de DKP (" + formatAmount(balance) + ").").show();
				return;
			}

			$button.attr('disabled', null);
		}
	}

	$quantity.on("keyup", update);
});

$("[data-order] button[data-action]").on("click", function (e) {
	e.preventDefault();
	e.stopImmediatePropagation();

	var $this = $(this)
	var $row = $this.closest("tr");

	var action = $this.data("action");
	var order = $row.data("order");

	$this.blur();
	$row.css({
		"text-decoration": "line-through",
	});
	$("button", $row).attr("disabled", true);

	$.ajax({
		method: "GET",
		url: "/trades/validation/" + order + "/" + action,
	});
});
