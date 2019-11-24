$("#item-id").on("keyup", $.debounce(500, function () {
	var id = parseInt(this.value);
	if (!id) return;

	var $output = $("#item-output");
	var $content = $("main", $output);
	var $button = $("#item-create-button");

	$button.attr("disabled", "disabled");
	$output.hide();

	$.get("/auctions/create/fetch-item", { id: id })
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
					document.location.href = '/auctions/item/' + id;
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

$("[auction-link]").on("click", function () {
	document.location.href = $(this).attr("auction-link");
	return false;
});

$(function () {
	var $orderForm = $(".order-form");

	if ($orderForm.length < 1) return;

	var $warnBlock = $(".warning", $orderForm);
	var $errorBlock = $(".error", $orderForm);
	var $sendButton = $("#item-create-button", $orderForm);

	$("input[name=kind]", $orderForm).on("change", checkFormStatus);
	$("select", $orderForm).on("change", checkFormStatus);
	$("input[name=guild-order]", $orderForm).on("change", checkFormStatus);
	$(".qte-price input", $orderForm).on("keyup", checkFormStatus);

	$(".button-toggles label").on("click", function () {
		$("[disabled]:not(#item-create-button)").attr("disabled", null);
		$("input[name=guild-order]").each(function () {
			this.checked = false;
		});
		checkFormStatus();
	})

	$("input[name=guild-order]").on("click", function () {
		$("select[name=account]").attr("disabled", this.checked ? "disabled" : null);
	});

	function formatAmount(amount) {
		var int = Math.floor(amount / 100);
		var fractional = amount % 100;
		return "" + int + "." + (fractional < 10 ? "0" : "") + fractional;
	}

	function min(a, b) { return a <= b ? a : b; }

	function max(a, b) { return a >= b ? a : b; }

	function toFixed(a) { return Math.round(a * 100); }

	bids = bids.map(toFixed);
	asks = asks.map(toFixed);

	function checkFormStatus() {
		var orderKind = $("input[name=kind]:checked").val();
		var guildOrder = !!$("input[name=guild-order]:checked", $orderForm).val();

		var accountSelect = $("select[name=account]")[0];
		var withdrawLimit = toFixed(parseFloat(accountSelect
			? accountSelect.selectedOptions[0].dataset.withdrawLimit
			: $("input[name=account]")[0].dataset.withdrawLimit));

		var quantity = parseInt($("input[name=quantity]").val().replace(/[^0-9\.]/g, ""));
		var price = toFixed(parseFloat($("input[name=price]")
			.attr("placeholder",
				orderKind ?
					formatAmount(orderKind === "bid"
						? Math.ceil(bids.length ? bids.reduce(max) * 1.05 : 0)
						: Math.floor(asks.length ? asks.reduce(min) * 0.95 : 0))
					: null)
			.val().replace(/[^0-9\.]/g, "")));

		var isValid = quantity && price;
		var errorText = null;
		var warnText = null;

		if (isValid) {
			if (orderKind === "bid" && !guildOrder && (quantity * price > withdrawLimit)) {
				errorText = "Le montant total est supérieur au solde de DKP disponibles.";
			} else {
				var others = (orderKind === "bid" ? bids : asks) || [];
				if (others.indexOf(price) < 0) {
					var conflicts = others.filter(function (existing) {
						return Math.abs(existing - price) < (0.05 * existing);
					});

					if (conflicts.length) {
						errorText = "Votre offre est trop proche d'une offre existante.";

						var minOffer = others.reduce(min);
						var maxOffer = others.reduce(max);
						var minTarget = Math.floor(minOffer * 0.95);
						var maxTarget = Math.ceil(maxOffer * 1.05);

						if (minTarget < price && price < minOffer) {
							errorText += "\nSous-enchère minimum: " + formatAmount(minTarget);
						} else if (maxTarget > price && price > maxOffer) {
							errorText += "\nSur-enchère minimum: " + formatAmount(maxTarget);
						}
					}
				}
			}

			if (orderKind === "ask") {
				var tax = Math.round(quantity * price * tradeTax);
				warnText = "Commission: <b>" + formatAmount(tax) + "</b>;  Montant reçu: <b>" + formatAmount(quantity * price - tax)
					+ "</b>\n<em>(Ne s'applique pas à une vente à la guilde)</em>";
			}
		}

		if (warnText) $warnBlock.html(warnText).show(); else $warnBlock.hide();
		if (errorText) $errorBlock.text(errorText).show(); else $errorBlock.hide();
		$sendButton.attr("disabled", isValid && !errorText ? null : "disabled");
	}

	checkFormStatus();
});

$(function () {
	$("[data-timer]").each(function () {
		this.dataset.timer = Math.floor(Date.now() / 1000) + parseInt(this.dataset.timer);
		$(this).css("visibility", "visible");
	});

	function pad(n) {
		return n < 10 ? "0" + n : "" + n;
	}

	function updateTimers() {
		$("[data-timer]").each(function () {
			var left = this.dataset.timer - Math.floor(Date.now() / 1000);

			if (left < 0) {
				$("span", this).text("");
			} else {
				var seconds = Math.floor((left % 60) / 5) * 5;
				var minutes = Math.floor(left / 60) % 60;
				var hours = Math.floor(left / 60 / 60);

				$("span", this).text((hours > 0 ? pad(hours) + ":" : "") + pad(minutes) + ":" + pad(seconds));
			}
		})
	}

	if ($("[data-timer]").length > 0) {
		setInterval(updateTimers, 5000);
		updateTimers();
	}
})
