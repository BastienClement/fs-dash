$(function () {
	$toast = $(".toast");
	$modal = $(".modal");

	let toastTimeout = null;

	function toast(type, text, duration) {
		clearTimeout(toastTimeout);
		$toast.attr("class", type + " toast").text(text).show();
		if (duration) {
			toastTimeout = setTimeout(() => $toast.hide(), duration);
		}
	}

	function clearToast() {
		clearTimeout(toastTimeout);
		$toast.hide();
	}

	const warn = (text, duration) => toast("warning", text, duration);
	const error = (text, duration) => toast("error", text, duration);

	document.addEventListener("paste", (event) => {
		const url = event.clipboardData.getData("text/plain");
		if (!url) return;

		const $container = $(".image-container", $modal);
		$container.html("");
		$container.append($("<img>").attr("src", url));
		$modal.show();

		warn("Processing image...");

		$.get(processUrl + encodeURIComponent(url)).then(
			function (res) {
				clearToast();
				$("img", $modal).css({opacity: .2});

				const matches = {};

				for (let annotation of res) {
					const left = annotation.rect.x - 5;
					const top = annotation.rect.y - 5;

					const div = $("<div>")
						.attr("class", "ocr-result" + (annotation.matches.length ? "" : " empty"))
						.css({
							left: annotation.rect.x - 5,
							top: annotation.rect.y - 5,
							width: annotation.rect.w + 10,
							height: annotation.rect.h + 10,
							backgroundImage: `url(${url})`,
							backgroundPosition: `-${left + 1}px -${top + 1}px`
						});

					if (annotation.matches.length) {
						const firstMatch = annotation.matches[0]

						if (!matches[firstMatch.user.id]) {
							matches[firstMatch.user.id] = [];
						}

						matches[firstMatch.user.id].push(div);

						div.append(
							$("<span>")
								.text(firstMatch.user.username + (!firstMatch.user.isPvP ? "*" : ""))
								.css({
									color: firstMatch.user.color
								})
						);

						((id) => {
							div.on("click", () => {
								matches[id].forEach(div => div.remove());
							});
						})(firstMatch.user.id);
					} else {
						((div) => div.on("click", () => div.remove()))(div);
					}

					$container.append(div);
				}
			},
			(err) => {
				error(err.responseJSON.err, 4000);
				$modal.hide();
			}
		);
	});

	document.addEventListener("keydown", (e) => {
		if (e.key === "Escape") {
			$modal.hide();
		}
	})
});
