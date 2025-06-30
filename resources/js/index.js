import * as echarts from 'echarts/core';
import './htmx.js';
import 'htmx-ext-ws/dist/ws.js';
import './shadow.js';

import { LineChart } from 'echarts/charts';

import {
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  DataZoomComponent
} from 'echarts/components';

import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  LineChart,
  TooltipComponent,
  GridComponent,
  DatasetComponent,
  CanvasRenderer
]);
