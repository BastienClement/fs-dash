document.addEventListener("paste", (event) => {
	const url = event.clipboardData.getData("text/plain");
	$.get(processUrl + encodeURIComponent(url));
});
