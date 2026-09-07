(function () {
  "use strict";

  const API = ""; // same origin as the served page
  const $ = (id) => document.getElementById(id);

  let products = [];
  let filtered = [];
  // cart: { [productId]: { product, qty } }
  const cart = {};
  let detailProduct = null;

  // ---- helpers -------------------------------------------------------------
  function show(el) { el.classList.remove("hidden"); }
  function hide(el) { el.classList.add("hidden"); }

  function money(n) {
    const v = Number(n || 0);
    return "$" + v.toFixed(2);
  }

  function escapeHtml(s) {
    return String(s == null ? "" : s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function flash(el, message, ok) {
    el.textContent = message;
    el.className = "alert " + (ok ? "alert-ok" : "alert-error");
    show(el);
  }

  // Placeholder image (inline SVG) when a product has no usable image.
  const PLACEHOLDER =
    "data:image/svg+xml;utf8," +
    encodeURIComponent(
      '<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400">' +
        '<rect width="100%" height="100%" fill="#eef0f7"/>' +
        '<text x="50%" y="50%" font-family="sans-serif" font-size="22" fill="#b7bcd0" ' +
        'text-anchor="middle" dominant-baseline="middle">Radiance</text></svg>'
    );

  function imageFor(p) {
    return p.image && p.image.trim() ? p.image : PLACEHOLDER;
  }

  // ---- catalog -------------------------------------------------------------
  async function loadProducts() {
    try {
      const res = await fetch(API + "/api/products");
      if (!res.ok) throw new Error("Status " + res.status);
      products = await res.json();
      filtered = products.slice();
      hide($("statusMsg"));
      renderGrid();
    } catch (err) {
      flash($("statusMsg"), "Could not load products. Please try again later.", false);
      $("grid").innerHTML = "";
    }
  }

  function renderGrid() {
    const grid = $("grid");
    grid.innerHTML = "";
    $("catalogCount").textContent =
      filtered.length + (filtered.length === 1 ? " item" : " items");
    if (!filtered.length) {
      show($("emptyState"));
      return;
    }
    hide($("emptyState"));
    filtered.forEach((p) => grid.appendChild(card(p)));
  }

  function card(p) {
    const el = document.createElement("article");
    el.className = "card product-card";
    const soldOut = !(p.quantity > 0);
    el.innerHTML =
      '<div class="card-media">' +
        '<img loading="lazy" alt="' + escapeHtml(p.name) + '" src="' + escapeHtml(imageFor(p)) + '" />' +
        (soldOut ? '<span class="badge sold-out">Sold out</span>' : "") +
      "</div>" +
      '<div class="card-body">' +
        '<h3 class="card-name">' + escapeHtml(p.name) + "</h3>" +
        '<p class="card-price">' + money(p.price) + "</p>" +
        '<p class="card-stock">' + (soldOut ? "Out of stock" : (p.quantity + " in stock")) + "</p>" +
      "</div>" +
      '<div class="card-actions"></div>';

    el.querySelector(".card-media img").addEventListener("error", function () {
      this.src = PLACEHOLDER;
    });
    el.querySelector(".card-media").addEventListener("click", () => openDetail(p));
    el.querySelector(".card-name").addEventListener("click", () => openDetail(p));

    const actions = el.querySelector(".card-actions");
    const add = document.createElement("button");
    add.className = "btn btn-primary btn-block";
    add.textContent = soldOut ? "Sold out" : "Add to cart";
    add.disabled = soldOut;
    add.addEventListener("click", () => addToCart(p));
    actions.appendChild(add);
    return el;
  }

  function applySearch() {
    const q = $("search").value.trim().toLowerCase();
    filtered = !q
      ? products.slice()
      : products.filter(
          (p) =>
            (p.name || "").toLowerCase().includes(q) ||
            (p.description || "").toLowerCase().includes(q)
        );
    renderGrid();
  }

  // ---- product detail ------------------------------------------------------
  function openDetail(p) {
    detailProduct = p;
    $("detailImg").src = imageFor(p);
    $("detailImg").alt = p.name || "";
    $("detailImg").onerror = function () { this.src = PLACEHOLDER; };
    $("detailName").textContent = p.name || "";
    $("detailPrice").textContent = money(p.price);
    const soldOut = !(p.quantity > 0);
    $("detailStock").textContent = soldOut ? "Out of stock" : (p.quantity + " in stock");
    $("detailDesc").textContent = p.description || "No description available.";
    const addBtn = $("detailAdd");
    addBtn.disabled = soldOut;
    addBtn.textContent = soldOut ? "Sold out" : "Add to cart";
    show($("detailOverlay"));
  }

  function closeDetail() { hide($("detailOverlay")); }

  // ---- cart ----------------------------------------------------------------
  function addToCart(p) {
    if (!(p.quantity > 0)) return;
    const entry = cart[p.id] || { product: p, qty: 0 };
    if (entry.qty >= p.quantity) {
      openCart();
      return;
    }
    entry.qty += 1;
    entry.product = p;
    cart[p.id] = entry;
    renderCart();
    bumpCartCount();
  }

  function setQty(id, qty) {
    const entry = cart[id];
    if (!entry) return;
    const max = entry.product.quantity;
    qty = Math.max(0, Math.min(qty, max));
    if (qty === 0) {
      delete cart[id];
    } else {
      entry.qty = qty;
    }
    renderCart();
  }

  function cartLines() { return Object.values(cart); }

  function cartTotal() {
    return cartLines().reduce(
      (sum, e) => sum + Number(e.product.price) * e.qty,
      0
    );
  }

  function cartQtyTotal() {
    return cartLines().reduce((sum, e) => sum + e.qty, 0);
  }

  function updateCartCount() {
    $("cartCount").textContent = cartQtyTotal();
  }

  function bumpCartCount() {
    updateCartCount();
    const b = $("cartBtn");
    b.classList.remove("bump");
    // force reflow to restart the animation
    void b.offsetWidth;
    b.classList.add("bump");
  }

  function renderCart() {
    const wrap = $("cartItems");
    wrap.innerHTML = "";
    const lines = cartLines();
    if (!lines.length) {
      show($("cartEmpty"));
    } else {
      hide($("cartEmpty"));
    }
    lines.forEach((e) => {
      const p = e.product;
      const row = document.createElement("div");
      row.className = "cart-row";
      row.innerHTML =
        '<img class="cart-thumb" alt="' + escapeHtml(p.name) + '" src="' + escapeHtml(imageFor(p)) + '" />' +
        '<div class="cart-meta">' +
          '<div class="cart-name">' + escapeHtml(p.name) + "</div>" +
          '<div class="muted">' + money(p.price) + " each</div>" +
        "</div>" +
        '<div class="qty">' +
          '<button class="qty-btn" data-act="dec" aria-label="Decrease">&minus;</button>' +
          '<span class="qty-val">' + e.qty + "</span>" +
          '<button class="qty-btn" data-act="inc" aria-label="Increase">+</button>' +
        "</div>" +
        '<div class="cart-line-total">' + money(Number(p.price) * e.qty) + "</div>";
      row.querySelector(".cart-thumb").addEventListener("error", function () {
        this.src = PLACEHOLDER;
      });
      row.querySelector('[data-act="dec"]').addEventListener("click", () => setQty(p.id, e.qty - 1));
      row.querySelector('[data-act="inc"]').addEventListener("click", () => setQty(p.id, e.qty + 1));
      wrap.appendChild(row);
    });
    $("cartTotal").textContent = money(cartTotal());
    $("checkoutBtn").disabled = !lines.length;
    updateCartCount();
  }

  function openCart() { renderCart(); show($("cartOverlay")); }
  function closeCart() { hide($("cartOverlay")); }

  // ---- checkout ------------------------------------------------------------
  async function checkout() {
    const lines = cartLines();
    if (!lines.length) return;
    const btn = $("checkoutBtn");
    btn.disabled = true;
    hide($("checkoutMsg"));

    const results = [];
    for (const e of lines) {
      try {
        const res = await fetch(API + "/api/purchases", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ productId: e.product.id, quantity: e.qty }),
        });
        if (!res.ok) {
          const err = await res.json().catch(() => ({}));
          throw new Error(err.message || "Purchase failed (" + res.status + ")");
        }
        results.push({ product: e.product, qty: e.qty });
      } catch (err) {
        flash(
          $("checkoutMsg"),
          "Checkout failed for " + e.product.name + ": " + err.message,
          false
        );
        btn.disabled = false;
        return;
      }
    }

    showConfirmation(results);
    // clear cart
    Object.keys(cart).forEach((k) => delete cart[k]);
    renderCart();
    closeCart();
    // refresh stock levels
    loadProducts();
  }

  function showConfirmation(results) {
    const summary = $("confirmSummary");
    summary.innerHTML = "";
    let total = 0;
    results.forEach((r) => {
      const line = Number(r.product.price) * r.qty;
      total += line;
      const row = document.createElement("div");
      row.className = "confirm-row";
      row.innerHTML =
        "<span>" + escapeHtml(r.product.name) + " × " + r.qty + "</span>" +
        "<span>" + money(line) + "</span>";
      summary.appendChild(row);
    });
    $("confirmTotal").textContent = money(total);
    show($("confirmOverlay"));
  }

  // ---- wiring --------------------------------------------------------------
  document.addEventListener("DOMContentLoaded", function () {
    $("cartBtn").addEventListener("click", openCart);
    $("cartClose").addEventListener("click", closeCart);
    $("detailClose").addEventListener("click", closeDetail);
    $("detailAdd").addEventListener("click", function () {
      if (detailProduct) {
        addToCart(detailProduct);
        closeDetail();
        openCart();
      }
    });
    $("checkoutBtn").addEventListener("click", checkout);
    $("confirmClose").addEventListener("click", () => hide($("confirmOverlay")));
    $("search").addEventListener("input", applySearch);

    // click outside modal content closes overlays
    document.querySelectorAll(".overlay").forEach((ov) => {
      ov.addEventListener("click", (e) => {
        if (e.target === ov) hide(ov);
      });
    });
    document.addEventListener("keydown", (e) => {
      if (e.key === "Escape") {
        document.querySelectorAll(".overlay").forEach((ov) => hide(ov));
      }
    });

    loadProducts();
  });
})();
