package com.example.facebookcleaner

internal object FacebookSponsoredFilter {
    val script = """
        (function() {
            if (window.__facebookCleanerInjected) return;
            window.__facebookCleanerInjected = true;

            const labels = [
                'sponsored',
                'sponsorowane',
                'sponsorowana',
                'promowane',
                'reklama',
                'paid partnership',
                'współpraca płatna'
            ];

            const normalize = (value) => (value || '')
                .replace(/\s+/g, ' ')
                .trim()
                .toLowerCase();

            const isSponsored = (element) => {
                const text = normalize(element.innerText);
                const ariaLabel = normalize(element.getAttribute('aria-label'));
                return labels.some(label =>
                    text.includes(label) || ariaLabel.includes(label)
                );
            };

            const hideSponsored = () => {
                const cards = document.querySelectorAll(
                    '[role="article"], article, [data-pagelet*="FeedUnit"], ' +
                    '[data-pagelet*="FeedStory"]'
                );

                cards.forEach(card => {
                    if (isSponsored(card)) {
                        card.style.setProperty('display', 'none', 'important');
                        card.setAttribute('data-facebook-cleaner-hidden', 'sponsored');
                    }
                });
            };

            hideSponsored();
            new MutationObserver(hideSponsored).observe(document.documentElement, {
                childList: true,
                subtree: true
            });
        })();
    """.trimIndent()
}
