import { createStore, produce } from "solid-js/store";

import type { Base64Image } from "~/common";

const IMAGES_MAX_SIZE = 25;

export type DebugState = ReturnType<typeof createDebugState>;

export function createDebugState() {
  const [images, setImages] = createStore<Base64Image[]>([]);

  function addImage(img: Base64Image) {
    setImages(produce(curr => {
      if (curr.push(img) > IMAGES_MAX_SIZE) {
        curr.shift();
      }
    }));
  }

  return {
    images,

    addImage
  };
}
