$(function () {
	var $sendForm = $(".send-amount");
	if ($sendForm.length < 1) return;

	var $errorBlock = $(".error", $sendForm);
	var $warnBlock = $(".warning", $sendForm);
	var $sendButton = $(".actions button", $sendForm);

	$("select[name=Destinataire]", $sendForm).on("change", checkFormStatus);
	$("input[name=Montant]", $sendForm).on("keyup", checkFormStatus);

	function formatAmount(amount) {
		var int = Math.floor(amount / 100);
		var fractional = amount % 100;
		return "" + int + "." + (fractional < 10 ? "0" : "") + fractional;
	}

	function toFixed(a) { return Math.round(a * 100); }

	function checkFormStatus() {
		var recipent = $("select[name=Destinataire]").val();
		var amount = toFixed(parseFloat($("input[name=Montant]").val().replace(/[^0-9\.]/g, "")));

		var errorText = null;
		var warnText = null;

		if (amount && amount > 0) {
			if (amount > tradeAvailable) {
				errorText = "Solde de DKP insufisant.";
			} else {
				var tax = Math.ceil(amount * tradeTax);
				warnText = "Commission: <b>" + formatAmount(tax) + "</b>;  Montant reçu: <b>" + formatAmount(amount - tax) + "</b>";
			}
		}

		if (warnText) $warnBlock.html(warnText).show(); else $warnBlock.hide();
		if (errorText) $errorBlock.text(errorText).show(); else $errorBlock.hide();
		$sendButton.attr("disabled", amount && amount > 0 && recipent && !errorText ? null : "disabled");
	}

	checkFormStatus();
});

