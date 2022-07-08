/* eslint-disable */

/*!
 * https://github.com/apvarun/toastify-js
 * @license MIT licensed
 *
 * Copyright (C) 2018 Varun A P
 */

class Toastify {
  constructor(options) {
    this.defaults = {
      oldestFirst: true,
      text: "",
      node: undefined,
      duration: 5000,
      selector: undefined,
      callback: function() {},
      destination: undefined,
      newWindow: false,
      gravity: "toastify-top",
      positionLeft: false,
      position: "center",
      className: "",
      stopOnFocus: true,
      onClick: function() {},
      offset: { x: 0, y: 0 },
      escapeMarkup: true,
      style: { background: "" },
    };
    this.options = {};

    this.toastElement = null;
    this._rootElement = document.body;

    this._init(options);
  }

  showToast() {
    this.toastElement = this._buildToast();

    if (typeof this.options.selector === "string") {
      this._rootElement = document.getElementById(this.options.selector);
    } else if (this.options.selector instanceof HTMLElement || this.options.selector instanceof ShadowRoot) {
      this._rootElement = this.options.selector;
    } else {
      this._rootElement = document.body;
    }

    if (!this._rootElement) {
      throw "Root element is not defined";
    }

    this._rootElement.insertBefore(this.toastElement, this._rootElement.firstChild);

    this._reposition();

    if (this.options.duration > 0) {
      this.toastElement.timeOutValue = window.setTimeout(
        () => {
          this._removeElement(this.toastElement);
        },
        this.options.duration
      );
    }

    return this;
  }

  hideToast() {
    if (this.toastElement.timeOutValue) {
      clearTimeout(this.toastElement.timeOutValue);
    }
    this._removeElement(this.toastElement);
  }

  _init(options) {
    this.options = Object.assign(this.defaults, options);

    this.toastElement = null;

    this.options.gravity = options.gravity === "bottom" ? "toastify-bottom" : "toastify-top";
    this.options.stopOnFocus = options.stopOnFocus === undefined ? true : options.stopOnFocus;
  }

  _buildToast() {
    if (!this.options) {
      throw "Toastify is not initialized";
    }

    let divElement = document.createElement("div");
    divElement.className = `toastify on ${this.options.className}`;
    divElement.className += ` toastify-${this.options.position}`;
    divElement.className += ` ${this.options.gravity}`;

    for (const property in this.options.style) {
      divElement.style[property] = this.options.style[property];
    }

    if (this.options.node && this.options.node.nodeType === Node.ELEMENT_NODE) {
      divElement.appendChild(this.options.node);
    } else {
      if (this.options.escapeMarkup) {
        divElement.innerText = this.options.text;
      } else {
        divElement.innerHTML = this.options.text;
      }
    }

    if (this.options.stopOnFocus && this.options.duration > 0) {
      // stop countdown
      divElement.addEventListener(
        "mouseover",
        () => {
          window.clearTimeout(divElement.timeOutValue);
        }
      );
      // add back the timeout
      divElement.addEventListener(
        "mouseleave",
        () => {
          divElement.timeOutValue = window.setTimeout(
            () => {
              // Remove the toast from DOM
              this._removeElement(divElement);
            },
            this.options.duration
          );
        }
      );
    }

    if (typeof this.options.destination !== "undefined") {
      divElement.addEventListener(
        "click",
        (event) => {
          event.stopPropagation();
          if (this.options.newWindow === true) {
            window.open(this.options.destination, "_blank");
          } else {
            window.location = this.options.destination;
          }
        }
      );
    }

    if (typeof this.options.onClick === "function" && typeof this.options.destination === "undefined") {
      divElement.addEventListener(
        "click",
        (event) => {
          event.stopPropagation();
          this.options.onClick();
        }
      );
    }

    if (typeof this.options.offset === "object") {
      const x = this._getAxisOffsetAValue("x", this.options);
      const y = this._getAxisOffsetAValue("y", this.options);

      const xOffset = this.options.position == "left" ? x : `-${x}`;
      const yOffset = this.options.gravity == "toastify-top" ? y : `-${y}`;

      divElement.style.transform = `translate(${xOffset},${yOffset})`;
    }

    return divElement;
  }

  _removeElement(toastElement) {
    toastElement.className = toastElement.className.replace(" on", "");

    window.setTimeout(
      () => {
        if (this.options.node && this.options.node.parentNode) {
          this.options.node.parentNode.removeChild(this.options.node);
        }

        if (toastElement.parentNode) {
          toastElement.parentNode.removeChild(toastElement);
        }

        this.options.callback.call(toastElement);

        this._reposition();
      },
      400
    );
  }

  _reposition() {
    let topLeftOffsetSize = {
      top: 15,
      bottom: 15,
    };
    let topRightOffsetSize = {
      top: 15,
      bottom: 15,
    };
    let offsetSize = {
      top: 15,
      bottom: 15,
    };

    let allToasts = this._rootElement.querySelectorAll(".toastify");

    let classUsed;
    for (let i = 0; i < allToasts.length; i++) {
      if (allToasts[i].classList.contains("toastify-top") === true) {
        classUsed = "toastify-top";
      } else {
        classUsed = "toastify-bottom";
      }

      let height = allToasts[i].offsetHeight;
      classUsed = classUsed.substr(9, classUsed.length - 1);
      let offset = 15;

      let width = window.innerWidth > 0 ? window.innerWidth : screen.width;

      if (width <= 360) {
        allToasts[i].style[classUsed] = `${offsetSize[classUsed]}px`;
        offsetSize[classUsed] += height + offset;
      } else {
        if (allToasts[i].classList.contains("toastify-left") === true) {
          allToasts[i].style[classUsed] = `${topLeftOffsetSize[classUsed]}px`;
          topLeftOffsetSize[classUsed] += height + offset;
        } else {
          allToasts[i].style[classUsed] = `${topRightOffsetSize[classUsed]}px`;
          topRightOffsetSize[classUsed] += height + offset;
        }
      }
    }
  }

  _getAxisOffsetAValue(axis, options) {
    if (options.offset[axis]) {
      if (isNaN(options.offset[axis])) {
        return options.offset[axis];
      } else {
        return `${options.offset[axis]}px`;
      }
    }

    return "0px";
  }
}

window.Toastify = Toastify;
