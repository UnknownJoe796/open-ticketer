package com.lightningkite.lskiteuistarter

import kotlinx.html.*

// Ivy Leaf Events brand colors
private const val IVY_GREEN = "#1B3D2F"
private const val IVY_GREEN_LIGHT = "#2A5A45"
private const val SAGE_MIST = "#8FAE8B"
private const val ANTIQUE_GOLD = "#C9A227"
private const val WARM_CREAM = "#FAF7F2"
private const val CHARCOAL = "#2C2C2C"
private const val MUTED = "#6B7B6E"

// Email-safe serif fonts — web fonts like Cormorant Garamond/Lora don't load in most email clients
private const val FONT_DISPLAY = "Georgia, 'Times New Roman', Times, serif"
private const val FONT_BODY = "Georgia, 'Times New Roman', Times, serif"

private fun style(vararg pairs: Pair<String, String>) =
    pairs.joinToString(";") { "${it.first}:${it.second}" }

/** Reusable table attrs helper for email-safe tables */
private fun TABLE.emailTable() {
    attributes["align"] = "center"
    attributes["width"] = "100%"
    attributes["border"] = "0"
    attributes["cellpadding"] = "0"
    attributes["cellspacing"] = "0"
    role = "presentation"
}

interface EmailContentBuilder {
    /** Large section header in ivy green */
    fun header(text: String)
    /** Body paragraph in charcoal */
    fun paragraph(text: String)
    /** CTA button in ivy green */
    fun buttonLink(text: String, href: String)
    /** Large monospace code display (e.g. verification PIN) */
    fun code(text: String)
    /** Inline image via CID attachment */
    fun inlineImage(src: String, alt: String, width: String, height: String)
    /** Personalized greeting (e.g. "Name, you've got tickets!") */
    fun greeting(name: String, message: String)
    /** Full-width hero image that bleeds to card edges */
    fun heroImage(src: String, alt: String)
    /** Structured detail row with label and value (e.g. "Date" / "March 15, 2026") */
    fun detailRow(label: String, value: String)
    /** Gold accent divider */
    fun divider()
    /** Smaller section header */
    fun subheader(text: String)
    /** Centered muted text */
    fun note(text: String)
}

fun HTML.emailBase(centralContent: EmailContentBuilder.() -> Unit) {
    dir = Dir.ltr
    lang = "en"
    head {
        meta {
            content = "text/html; charset=UTF-8"
            attributes["http-equiv"] = "Content-Type"
        }
    }
    body {
        style = "margin:0;padding:0;background-color:$WARM_CREAM"
        // Outer wrapper for cream background
        table {
            emailTable()
            style = "background-color:$WARM_CREAM"
            tbody {
                tr {
                    td {
                        style = "padding:32px 16px"
                        // Inner card
                        table {
                            emailTable()
                            style = style(
                                "max-width" to "600px",
                                "margin" to "0 auto",
                                "background-color" to "#ffffff",
                                "border-radius" to "8px",
                                "box-shadow" to "0 4px 12px rgba(27,61,47,0.1)",
                                "overflow" to "hidden",
                            )
                            tbody {
                                // Branded header bar
                                tr {
                                    td {
                                        style = style(
                                            "background-color" to IVY_GREEN,
                                            "padding" to "24px 32px",
                                            "text-align" to "center",
                                        )
                                        h1 {
                                            style = style(
                                                "margin" to "0",
                                                "font-family" to FONT_DISPLAY,
                                                "font-size" to "28px",
                                                "font-weight" to "600",
                                                "color" to WARM_CREAM,
                                                "letter-spacing" to "1px",
                                            )
                                            +"Ivy Leaf Events"
                                        }
                                    }
                                }
                                // Gold accent line
                                tr {
                                    td {
                                        style = "padding:0"
                                        div {
                                            style = "height:3px;background-color:$ANTIQUE_GOLD"
                                        }
                                    }
                                }
                                // Content area
                                tr {
                                    td {
                                        style = "padding:32px"
                                        centralContent(object : EmailContentBuilder {
                                            override fun header(text: String) = with(this@td) {
                                                h2 {
                                                    style = style(
                                                        "font-family" to FONT_DISPLAY,
                                                        "font-size" to "24px",
                                                        "font-weight" to "600",
                                                        "color" to IVY_GREEN,
                                                        "margin" to "0 0 16px 0",
                                                    )
                                                    +text
                                                }
                                            }

                                            override fun paragraph(text: String) = with(this@td) {
                                                p {
                                                    style = style(
                                                        "font-family" to FONT_BODY,
                                                        "font-size" to "16px",
                                                        "line-height" to "1.7",
                                                        "color" to CHARCOAL,
                                                        "margin" to "0 0 12px 0",
                                                    )
                                                    +text
                                                }
                                            }

                                            override fun buttonLink(text: String, href: String) = with(this@td) {
                                                table {
                                                    emailTable()
                                                    style = "margin:20px auto;width:280px"
                                                    tbody {
                                                        tr {
                                                            td {
                                                                style = style(
                                                                    "background-color" to IVY_GREEN,
                                                                    "padding" to "14px 24px",
                                                                    "border-radius" to "8px",
                                                                    "text-align" to "center",
                                                                )
                                                                a {
                                                                    this.href = href
                                                                    style = style(
                                                                        "text-decoration" to "none",
                                                                        "color" to WARM_CREAM,
                                                                        "font-family" to FONT_DISPLAY,
                                                                        "font-size" to "20px",
                                                                        "font-weight" to "700",
                                                                        "letter-spacing" to "0.5px",
                                                                        "display" to "inline-block",
                                                                        "width" to "100%",
                                                                    )
                                                                    +text
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            override fun code(text: String) = with(this@td) {
                                                table {
                                                    emailTable()
                                                    style = "margin:20px auto;width:280px"
                                                    tbody {
                                                        tr {
                                                            td {
                                                                style = style(
                                                                    "background-color" to WARM_CREAM,
                                                                    "border" to "2px solid $ANTIQUE_GOLD",
                                                                    "border-radius" to "8px",
                                                                    "padding" to "20px",
                                                                )
                                                                p {
                                                                    style = style(
                                                                        "font-family" to "monospace",
                                                                        "font-size" to "32px",
                                                                        "line-height" to "40px",
                                                                        "font-weight" to "700",
                                                                        "letter-spacing" to "6px",
                                                                        "color" to IVY_GREEN,
                                                                        "text-align" to "center",
                                                                        "margin" to "0",
                                                                    )
                                                                    +text
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            override fun inlineImage(src: String, alt: String, width: String, height: String) = with(this@td) {
                                                img {
                                                    this.src = src
                                                    this.alt = alt
                                                    style = style(
                                                        "width" to width,
                                                        "height" to height,
                                                        "margin" to "16px auto",
                                                        "display" to "block",
                                                        "border-radius" to "8px",
                                                    )
                                                }
                                            }

                                            override fun greeting(name: String, message: String) = with(this@td) {
                                                h2 {
                                                    style = style(
                                                        "font-family" to FONT_DISPLAY,
                                                        "font-size" to "28px",
                                                        "font-weight" to "700",
                                                        "color" to IVY_GREEN,
                                                        "margin" to "0 0 4px 0",
                                                        "text-align" to "center",
                                                    )
                                                    +name
                                                }
                                                p {
                                                    style = style(
                                                        "font-family" to FONT_DISPLAY,
                                                        "font-size" to "22px",
                                                        "font-weight" to "400",
                                                        "color" to IVY_GREEN_LIGHT,
                                                        "margin" to "0 0 24px 0",
                                                        "text-align" to "center",
                                                    )
                                                    +message
                                                }
                                            }

                                            override fun heroImage(src: String, alt: String) = with(this@td) {
                                                // Negative margin to bleed to card edges within the 32px padding
                                                img {
                                                    this.src = src
                                                    this.alt = alt
                                                    style = style(
                                                        "width" to "calc(100% + 64px)",
                                                        "max-width" to "none",
                                                        "height" to "auto",
                                                        "margin" to "0 -32px 24px -32px",
                                                        "display" to "block",
                                                        "object-fit" to "cover",
                                                        "max-height" to "280px",
                                                    )
                                                }
                                            }

                                            override fun detailRow(label: String, value: String) = with(this@td) {
                                                table {
                                                    emailTable()
                                                    style = "margin:0 0 8px 0"
                                                    tbody {
                                                        tr {
                                                            td {
                                                                style = style(
                                                                    "width" to "90px",
                                                                    "vertical-align" to "top",
                                                                    "padding" to "4px 0",
                                                                )
                                                                span {
                                                                    style = style(
                                                                        "font-family" to FONT_BODY,
                                                                        "font-size" to "14px",
                                                                        "color" to MUTED,
                                                                        "text-transform" to "uppercase",
                                                                        "letter-spacing" to "0.5px",
                                                                    )
                                                                    +label
                                                                }
                                                            }
                                                            td {
                                                                style = style(
                                                                    "vertical-align" to "top",
                                                                    "padding" to "4px 0",
                                                                )
                                                                span {
                                                                    style = style(
                                                                        "font-family" to FONT_BODY,
                                                                        "font-size" to "16px",
                                                                        "color" to CHARCOAL,
                                                                    )
                                                                    +value
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            override fun divider() = with(this@td) {
                                                div {
                                                    style = style(
                                                        "height" to "1px",
                                                        "background-color" to ANTIQUE_GOLD,
                                                        "margin" to "20px 0",
                                                        "opacity" to "0.5",
                                                    )
                                                }
                                            }

                                            override fun subheader(text: String) = with(this@td) {
                                                h3 {
                                                    style = style(
                                                        "font-family" to FONT_DISPLAY,
                                                        "font-size" to "20px",
                                                        "font-weight" to "600",
                                                        "color" to IVY_GREEN,
                                                        "margin" to "0 0 12px 0",
                                                    )
                                                    +text
                                                }
                                            }

                                            override fun note(text: String) = with(this@td) {
                                                p {
                                                    style = style(
                                                        "font-family" to FONT_BODY,
                                                        "font-size" to "14px",
                                                        "line-height" to "1.6",
                                                        "color" to MUTED,
                                                        "margin" to "0 0 12px 0",
                                                        "text-align" to "center",
                                                    )
                                                    +text
                                                }
                                            }
                                        })
                                    }
                                }
                                // Gold accent line before footer
                                tr {
                                    td {
                                        style = "padding:0 32px"
                                        div {
                                            style = "height:1px;background-color:$ANTIQUE_GOLD"
                                        }
                                    }
                                }
                                // Footer
                                tr {
                                    td {
                                        style = style(
                                            "padding" to "20px 32px",
                                            "text-align" to "center",
                                        )
                                        p {
                                            style = style(
                                                "font-family" to FONT_BODY,
                                                "font-size" to "13px",
                                                "color" to SAGE_MIST,
                                                "margin" to "0",
                                            )
                                            +"Ivy Leaf Events \u00B7 Creating Enchanted Evenings"
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
