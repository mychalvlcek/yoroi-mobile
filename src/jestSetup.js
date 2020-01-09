// @flow
/* eslint-env jest */
/* This module sets up Jest */
import fetch from 'node-fetch'
import {Logger, LogLevel} from './utils/logging'

import nodeUtil from 'util'

jest.setMock('./config', require('./__mocks__/config'))

// $FlowFixMe
global.TextEncoder = nodeUtil.TextEncoder
// $FlowFixMe
global.TextDecoder = nodeUtil.TextDecoder

export default {
  setup: () => {
    global.fetch = fetch
    Logger.setLogLevel(LogLevel.Warn)
  },
  debug: () => Logger.setLogLevel(LogLevel.Debug),
}
